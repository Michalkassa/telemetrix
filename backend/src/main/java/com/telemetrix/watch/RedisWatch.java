package com.telemetrix.watch;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

// Watch-together across instances (docs/adr/0008): each watch session is a JSON value in Redis
// ("watch:<id>", kept a day after its last change), so it survives restarts and any instance can serve it.
// Every clock change is published on one Redis channel; each instance relays what it hears to its own
// WebSocket viewers on /topic/watch/<id> — including the instance that made the change.
@Configuration(proxyBeanMethods = false)
class RedisWatch {

	static final String CHANNEL = "watch-clock";

	static final Duration KEPT = Duration.ofDays(1);

	private static final Logger log = LoggerFactory.getLogger(RedisWatch.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Bean
	WatchStore watchStore(StringRedisTemplate redis) {
		return new WatchStore() {

			@Override
			public boolean createIfAbsent(String watchId, Watch watch) {
				return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(watchId), JSON.writeValueAsString(watch), KEPT));
			}

			@Override
			public Optional<Watch> find(String watchId) {
				return Optional.ofNullable(redis.opsForValue().get(key(watchId))).map(json -> JSON.readValue(json, Watch.class));
			}

			@Override
			public void save(String watchId, Watch watch) {
				redis.opsForValue().set(key(watchId), JSON.writeValueAsString(watch), KEPT);
			}

			private static String key(String watchId) {
				return "watch:" + watchId;
			}

		};
	}

	@Bean
	WatchUpdates watchUpdates(StringRedisTemplate redis) {
		return state -> redis.convertAndSend(CHANNEL, JSON.writeValueAsString(state));
	}

	@Bean
	RedisMessageListenerContainer watchRelay(RedisConnectionFactory connections, SimpMessagingTemplate broker) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connections);
		container.addMessageListener((message, pattern) -> {
			try {
				ClockState state = JSON.readValue(message.getBody(), ClockState.class);
				broker.convertAndSend("/topic/watch/" + state.watchId(), state);
			}
			catch (RuntimeException e) {
				log.warn("Couldn't relay a watch-together update", e);
			}
		}, new ChannelTopic(CHANNEL));
		return container;
	}

}
