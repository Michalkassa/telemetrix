package com.telemetrix.watch;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// STOMP over WebSocket at /ws, with Spring's in-memory broker (docs/adr/0005). Clients send to /app/...,
// subscribe to /topic/...; subscribing to /app/... gets a direct reply.
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// same origin through nginx / the Vite proxy; the Vite dev server's own origin for direct use
		registry.addEndpoint("/ws").setAllowedOriginPatterns("http://localhost:*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
		// one message at a time per client, so a host's commands arrive in the order sent
		registry.setPreservePublishOrder(true);
	}

}
