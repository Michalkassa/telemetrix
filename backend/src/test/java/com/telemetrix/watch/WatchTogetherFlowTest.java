package com.telemetrix.watch;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.FakeTime;
import com.telemetrix.TestcontainersConfiguration;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// Outer test: watch-together over STOMP. A host and a viewer join the same watch session; the host's
// play / pause / seek / speed reach the viewer in order, and a late joiner gets the current position at
// once. The server's clock is a FakeTime, so "10 seconds of playing" takes no time at all.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, WatchTogetherFlowTest.ControlledTime.class })
class WatchTogetherFlowTest {

	static final Instant NOW = Instant.parse("2026-10-09T20:00:00Z");

	@TestConfiguration
	static class ControlledTime {

		@Bean
		@Primary
		FakeTime fakeTime() {
			return new FakeTime(NOW);
		}

	}

	@LocalServerPort
	int port;

	@Autowired
	RestTestClient http;

	@Autowired
	FakeTime time;

	WebSocketStompClient stomp;

	String watchId;

	String hostKey;

	@BeforeEach
	void createWatchSession() {
		stomp = new WebSocketStompClient(new StandardWebSocketClient());
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
		Map<String, Object> created = http.post().uri("/api/watch").body(Map.of("sessionId", 5)).exchange()
				.expectStatus().isCreated()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();
		watchId = (String) created.get("watchId");
		hostKey = (String) created.get("hostKey");
		assertThat(watchId).isNotBlank();
		assertThat(hostKey).isNotBlank().isNotEqualTo(watchId);
	}

	@AfterEach
	void stop() {
		stomp.stop();
	}

	@Test
	void theHostsCommandsReachTheViewerInOrderAndALateJoinerCatchesUp() throws Exception {
		StompSession host = connect();
		StompSession viewer = connect();
		BlockingQueue<ClockState> seen = subscribe(viewer, "/topic/watch/" + watchId);

		send(host, hostKey, "PLAY", null);
		ClockState playing = next(seen);
		time.advance(Duration.ofSeconds(10));
		send(host, hostKey, "PAUSE", null);
		ClockState paused = next(seen);
		send(host, hostKey, "SEEK", 600_000.0);
		ClockState sought = next(seen);
		send(host, hostKey, "SPEED", 4.0);
		ClockState faster = next(seen);

		assertThat(playing.playing()).isTrue();
		assertThat(playing.positionMs()).isZero();
		assertThat(playing.at()).isEqualTo(NOW);
		assertThat(paused.playing()).isFalse();
		assertThat(paused.positionMs()).isEqualTo(10_000); // 10 s of playing at 1×
		assertThat(sought.positionMs()).isEqualTo(600_000);
		assertThat(faster.speed()).isEqualTo(4.0);
		assertThat(faster.positionMs()).isEqualTo(600_000);
		assertThat(java.util.List.of(playing, paused, sought, faster)).extracting(ClockState::seq)
				.containsExactly(1L, 2L, 3L, 4L);
		assertThat(faster.sessionId()).isEqualTo(5);

		StompSession late = connect();
		BlockingQueue<ClockState> catchUp = subscribe(late, "/app/watch/" + watchId);
		assertThat(next(catchUp)).satisfies(now -> {
			assertThat(now.seq()).isEqualTo(4);
			assertThat(now.positionMs()).isEqualTo(600_000);
			assertThat(now.speed()).isEqualTo(4.0);
			assertThat(now.playing()).isFalse();
		});
	}

	@Test
	void onlyTheHostCanControlTheClock() throws Exception {
		StompSession host = connect();
		StompSession viewer = connect();
		BlockingQueue<ClockState> seen = subscribe(viewer, "/topic/watch/" + watchId);

		send(viewer, "not-the-key", "PLAY", null);
		send(host, hostKey, "SEEK", 5_000.0);

		ClockState first = next(seen);
		assertThat(first.seq()).isEqualTo(1); // the viewer's PLAY changed nothing
		assertThat(first.playing()).isFalse();
		assertThat(first.positionMs()).isEqualTo(5_000);
	}

	@Test
	void anUnknownWatchSessionIsNotFound() {
		http.get().uri("/api/watch/nope").exchange().expectStatus().isNotFound();
		http.get().uri("/api/watch/" + watchId).exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.sessionId").isEqualTo(5).jsonPath("$.hostKey").doesNotExist();
	}

	private StompSession connect() throws Exception {
		return stomp.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
	}

	private static BlockingQueue<ClockState> subscribe(StompSession session, String destination)
			throws InterruptedException {
		BlockingQueue<ClockState> received = new LinkedBlockingQueue<>();
		session.subscribe(destination, new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return ClockState.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((ClockState) payload);
			}

		});
		Thread.sleep(200); // let the subscription reach the broker before anything is sent
		return received;
	}

	private void send(StompSession session, String key, String action, Double value) {
		var command = new java.util.HashMap<String, Object>();
		command.put("hostKey", key);
		command.put("action", action);
		command.put("value", value);
		session.send("/app/watch/" + watchId + "/command", command);
	}

	private static ClockState next(BlockingQueue<ClockState> queue) throws InterruptedException {
		ClockState state = queue.poll(5, TimeUnit.SECONDS);
		assertThat(state).as("a clock update within 5 s").isNotNull();
		return state;
	}

}
