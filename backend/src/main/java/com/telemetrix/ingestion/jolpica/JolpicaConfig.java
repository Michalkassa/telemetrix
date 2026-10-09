package com.telemetrix.ingestion.jolpica;

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
@EnableConfigurationProperties(JolpicaProperties.class)
class JolpicaConfig {

	@Bean
	JolpicaClient jolpicaClient(RestClient.Builder builder, JolpicaProperties props, Clock clock, Sleeper sleeper) {
		var settings = HttpClientSettings.defaults()
				.withConnectTimeout(props.timeout())
				.withReadTimeout(props.timeout());
		RestClient http = builder.clone()
				.baseUrl(props.baseUrl())
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new JolpicaClient(new RateLimitedClient("Jolpica", http, props.maxAttempts(), props.initialBackoff(),
				props.minRequestInterval(), clock, sleeper));
	}

}
