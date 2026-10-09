package com.telemetrix.ingestion.multiviewer;

import com.telemetrix.common.Sleeper;
import com.telemetrix.ingestion.RateLimitedClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MultiviewerProperties.class)
class MultiviewerConfig {

	@Bean
	MultiviewerClient multiviewerClient(RestClient.Builder builder, MultiviewerProperties props, Clock clock,
			Sleeper sleeper) {
		var settings = HttpClientSettings.defaults()
				.withConnectTimeout(props.timeout())
				.withReadTimeout(props.timeout());
		RestClient http = builder.clone()
				.baseUrl(props.baseUrl())
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new MultiviewerClient(new RateLimitedClient("Multiviewer", http, props.maxAttempts(),
				props.initialBackoff(), props.minRequestInterval(), clock, sleeper));
	}

}
