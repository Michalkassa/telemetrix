package com.telemetrix.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.TestcontainersConfiguration;
import com.telemetrix.ingestion.RaceImported;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// Timeline updates pushed over the WebSocket (docs/adr/0005): when a race is imported again (a race still
// being run, or a re-import), everyone with it open hears that its timeline changed and fetches it again.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TimelineUpdatesFlowTest {

	@LocalServerPort
	int port;

	@Autowired
	ApplicationEventPublisher events;

	@Test
	@SuppressWarnings("unchecked")
	void aReimportedRaceTellsItsViewersTheTimelineChanged() throws Exception {
		var stomp = new WebSocketStompClient(new StandardWebSocketClient());
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
		var session = stomp.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
		BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
		session.subscribe("/topic/races/39/timeline", new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((Map<String, Object>) payload);
			}

		});
		Thread.sleep(200); // the subscription reaches the broker

		events.publishEvent(new RaceImported(38, List.of(14L))); // another race: not for these viewers
		events.publishEvent(new RaceImported(39, List.of(15L)));

		var update = received.poll(5, TimeUnit.SECONDS);
		assertThat(update).isNotNull().containsEntry("raceId", 39).containsEntry("change", "TIMELINE");
		assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
		stomp.stop();
	}

}
