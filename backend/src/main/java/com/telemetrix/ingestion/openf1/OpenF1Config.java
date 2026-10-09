package com.telemetrix.ingestion.openf1;

import com.telemetrix.common.Sleeper;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenF1Properties.class)
class OpenF1Config {

	@Bean
	OpenF1Client openF1Client(RestClient.Builder builder, OpenF1Properties props, Clock clock, Sleeper sleeper) {
		var settings = HttpClientSettings.defaults()
				.withConnectTimeout(props.timeout())
				.withReadTimeout(props.timeout());
		RestClient http = builder.baseUrl(props.baseUrl())
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new OpenF1Client(http, props, clock, sleeper);
	}

}
