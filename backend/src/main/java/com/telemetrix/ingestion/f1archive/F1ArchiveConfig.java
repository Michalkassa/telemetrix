package com.telemetrix.ingestion.f1archive;

import com.telemetrix.common.Sleeper;
import com.telemetrix.ingestion.RateLimitedClient;
import com.telemetrix.ingestion.TrackStatusSourceFactory;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(F1ArchiveProperties.class)
class F1ArchiveConfig {

	@Bean
	F1ArchiveClient f1ArchiveClient(RestClient.Builder builder, F1ArchiveProperties props, Clock clock,
			Sleeper sleeper) {
		var settings = HttpClientSettings.defaults()
				.withConnectTimeout(props.timeout())
				.withReadTimeout(props.timeout());
		RestClient http = builder.clone()
				.baseUrl(props.baseUrl())
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new F1ArchiveClient(new RateLimitedClient("F1 archive", http, props.maxAttempts(),
				props.initialBackoff(), props.minRequestInterval(), clock, sleeper));
	}

	@Bean
	TrackStatusSourceFactory trackStatusSourceFactory(F1ArchiveClient archive, F1ArchiveProperties props) {
		return new TrackStatusSourceFactory(archive, props.enabled());
	}

}
