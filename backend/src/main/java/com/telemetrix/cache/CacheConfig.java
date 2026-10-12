package com.telemetrix.cache;

import com.telemetrix.championship.ChampionshipStandings;
import com.telemetrix.event.Timeline;
import com.telemetrix.replay.ReplayResponse;
import com.telemetrix.state.ShippedSnapshots;
import com.telemetrix.telemetry.TelemetryResponse;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

// Spring Cache on Redis (docs/adr/0008): every instance shares one cache, and it survives restarts. Each
// cache holds one type as JSON (no Java serialization), lives a while, and is evicted when what it was
// worked out from changes (CacheEvictions). Statistics on: hits and misses are the cache's metrics.
// Writes are immediate: by default Spring Data Redis may finish a put after the method has returned, so a
// request right after could miss.
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer caches(RedisConnectionFactory redis) {
		return builder -> builder
				.cacheWriter(RedisCacheWriter.create(redis, writer -> writer.immediateWrites().collectStatistics()))
				.disableCreateOnMissingCache() // a cache name nobody configured is a bug
				.withCacheConfiguration(Caches.TIMELINES, typed(Timeline.class, Duration.ofHours(12)))
				.withCacheConfiguration(Caches.REPLAYS, typed(ReplayResponse.class, Duration.ofHours(12)))
				.withCacheConfiguration(Caches.SNAPSHOTS, typed(ShippedSnapshots.class, Duration.ofHours(12)))
				.withCacheConfiguration(Caches.STANDINGS, typed(ChampionshipStandings.class, Duration.ofHours(12)))
				.withCacheConfiguration(Caches.TELEMETRY_LAPS, typed(TelemetryResponse.class, Duration.ofHours(1)));
	}

	private static RedisCacheConfiguration typed(Class<?> type, Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(type)));
	}

}
