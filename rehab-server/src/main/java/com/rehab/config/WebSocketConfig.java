package com.rehab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Value("${rehab.websocket.allowed-origin-patterns}")
    private String allowedOriginPatterns;
    private final com.rehab.websocket.JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor;

    public WebSocketConfig(com.rehab.websocket.JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor) {
        this.jwtWebSocketHandshakeInterceptor = jwtWebSocketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new com.rehab.websocket.AppointmentWebSocket(), "/ws/appointment")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns.split(","));
    }
}
