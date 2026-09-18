package com.ivan.nexus.infrastructure.terminal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "nexus.terminal.enabled", havingValue = "true")
public class TerminalWebSocketConfig implements WebSocketConfigurer {
    static final String ATTR_AUTHENTICATION = "nexus.terminal.authentication";
    static final String ATTR_CLIENT_IP = "nexus.terminal.clientIp";

    private final VpsTerminalWebSocketHandler handler;

    public TerminalWebSocketConfig(VpsTerminalWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/terminal/vps")
                .addInterceptors(new HttpSessionHandshakeInterceptor(), new AuthCapturingHandshakeInterceptor());
    }

    static final class AuthCapturingHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            attributes.put(ATTR_AUTHENTICATION, auth);
            if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
                attributes.put(ATTR_CLIENT_IP, request.getRemoteAddress().getAddress().getHostAddress());
            }
            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                attributes.put(ATTR_CLIENT_IP, forwarded.split(",", 2)[0].trim());
            }
            return true;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception) {
            // no-op
        }
    }
}
