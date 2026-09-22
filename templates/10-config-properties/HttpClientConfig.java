// TEMPLATE 10b — A @Configuration class: creating beans yourself
// Where it goes: src/main/java/com/telemetrix/<feature>/HttpClientConfig.java
//
// Most beans come from annotating YOUR classes (@Service, @Repository…). But for objects from a
// library, which you cannot annotate, you write a @Bean method: Spring calls it once at startup
// and makes the returned object injectable everywhere.
//
// Here: a RestClient preconfigured for the OpenF1 API (used in template 11).

package com.telemetrix.library;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    // Parameters of a @Bean method are injected like constructor parameters: Spring passes the
    // auto-configured RestClient.Builder and our properties record from 10a.
    @Bean
    RestClient openF1RestClient(RestClient.Builder builder, OpenF1Properties props) {
        var settings = HttpClientSettings.defaults()
                .withConnectTimeout(props.timeout())
                .withReadTimeout(props.timeout());
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}

// If two beans of the same type exist, say which one you want with @Qualifier("openF1RestClient")
// on the constructor parameter (the bean name is the method name).
