package com.telemetrix.watch;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TelemetrixBackendApplication;
import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.watch.WatchService.Command;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Watch-together across instances (docs/adr/0008): a watch session lives in Redis, so it survives a
// restart and any instance can serve it, and every change reaches the viewers connected to any instance
// (Redis pub/sub, each instance relaying to its own WebSocket clients). Two real application contexts
// here, on one Postgres and one Redis.
class WatchAcrossInstancesTest {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	static final GenericContainer<?> REDIS = new GenericContainer<>(TestcontainersConfiguration.REDIS)
			.withExposedPorts(6379);

	final List<ConfigurableApplicationContext> instances = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void start() {
		POSTGRES.start();
		REDIS.start();
	}

	@AfterAll
	static void stop() {
		REDIS.stop();
		POSTGRES.stop();
	}

	@AfterEach
	void closeInstances() {
		instances.forEach(ConfigurableApplicationContext::close);
	}

	@Test
	void aWatchSessionSurvivesARestart() {
		var first = instance();
		var watches = first.getBean(WatchService.class);
		var created = watches.create(15);
		watches.command(created.watchId(), new Command(created.hostKey(), "SEEK", 600_000.0));
		watches.command(created.watchId(), new Command(created.hostKey(), "SPEED", 2.0));
		first.close();

		var restarted = instance().getBean(WatchService.class);

		assertThat(restarted.state(created.watchId())).satisfies(state -> {
			assertThat(state.sessionId()).isEqualTo(15);
			assertThat(state.positionMs()).isEqualTo(600_000);
			assertThat(state.speed()).isEqualTo(2.0);
			assertThat(state.seq()).isEqualTo(2);
		});
		assertThat(restarted.command(created.watchId(), new Command(created.hostKey(), "PLAY", null))).isPresent();
	}

	@Test
	void aCommandOnOneInstanceReachesTheViewersOfAnother() throws Exception {
		var host = instance();
		var other = instance();
		List<Message<?>> toViewersOfOther = new CopyOnWriteArrayList<>();
		other.getBean("brokerChannel", SubscribableChannel.class).subscribe(toViewersOfOther::add);
		var created = host.getBean(WatchService.class).create(15);

		host.getBean(WatchService.class).command(created.watchId(), new Command(created.hostKey(), "SEEK", 90_000.0));

		var sent = await(toViewersOfOther, "/topic/watch/" + created.watchId());
		// on the broker channel the message is already converted: the JSON viewers get
		var state = JsonMapper.builder().build().readValue((byte[]) sent.getPayload(), ClockState.class);
		assertThat(state.positionMs()).isEqualTo(90_000);
		assertThat(state.watchId()).isEqualTo(created.watchId());
	}

	private Message<?> await(List<Message<?>> messages, String destination) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (System.nanoTime() < deadline) {
			var found = messages.stream()
					.filter(m -> destination.equals(SimpMessageHeaderAccessor.getDestination(m.getHeaders())))
					.findFirst();
			if (found.isPresent()) {
				return found.get();
			}
			Thread.sleep(20);
		}
		throw new AssertionError("Nothing sent to " + destination + " within 10 s");
	}

	private ConfigurableApplicationContext instance() {
		// as command-line arguments: they win over application.properties
		var context = new SpringApplicationBuilder(TelemetrixBackendApplication.class).run(
				"--server.port=0",
				"--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				"--spring.data.redis.host=" + REDIS.getHost(),
				"--spring.data.redis.port=" + REDIS.getMappedPort(6379));
		instances.add(context);
		return context;
	}

}
