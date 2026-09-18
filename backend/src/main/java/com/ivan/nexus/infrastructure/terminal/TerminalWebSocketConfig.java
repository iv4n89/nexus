package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "nexus.terminal.enabled", havingValue = "true")
public class TerminalWebSocketConfig implements WebSocketConfigurer {
    static final String ATTR_AUTHENTICATION = "nexus.terminal.authentication";
    static final String ATTR_CLIENT_IP = "nexus.terminal.clientIp";
    static final String ATTR_PROJECT_ID = "nexus.terminal.projectId";
    static final String ATTR_CONTAINER_ID = "nexus.terminal.containerId";

    private final VpsTerminalWebSocketHandler vpsHandler;
    private final ContainerTerminalWebSocketHandler containerHandler;
    private final NexusProperties properties;

    public TerminalWebSocketConfig(
            VpsTerminalWebSocketHandler vpsHandler,
            ContainerTerminalWebSocketHandler containerHandler,
            NexusProperties properties) {
        this.vpsHandler = vpsHandler;
        this.containerHandler = containerHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        HandshakeInterceptor[] common = {
                new HttpSessionHandshakeInterceptor(),
                new AuthCapturingHandshakeInterceptor(),
                new OriginCheckHandshakeInterceptor(properties.getTerminal().getAllowedOrigins())
        };
        registry.addHandler(vpsHandler, "/api/terminal/vps")
                .addInterceptors(common);
        registry.addHandler(containerHandler,
                        "/ws/terminal/projects/{projectId}/containers/{containerId}",
                        "/api/terminal/projects/{projectId}/containers/{containerId}")
                .addInterceptors(common[0], common[1], common[2], new PathVariableHandshakeInterceptor());
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

    /**
     * Rejects cross-origin websocket handshakes unless Origin is empty (non-browser)
     * or matches the Host / configured allow-list.
     */
    static final class OriginCheckHandshakeInterceptor implements HandshakeInterceptor {
        private final Set<String> allowedOrigins;

        OriginCheckHandshakeInterceptor(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null
                    ? Set.of()
                    : allowedOrigins.stream()
                            .filter(o -> o != null && !o.isBlank())
                            .map(String::trim)
                            .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
            if (origin == null || origin.isBlank()) {
                return true;
            }
            if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
                return true;
            }
            String host = request.getHeaders().getFirst(HttpHeaders.HOST);
            if (host != null && !host.isBlank()) {
                try {
                    URI originUri = URI.create(origin);
                    String originHost = originUri.getHost();
                    String hostOnly = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
                    if (originHost != null && originHost.equalsIgnoreCase(hostOnly)) {
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            }
            return false;
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

    static final class PathVariableHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            String path = request.getURI().getPath();
            String[] parts = path.split("/");
            // .../projects/{projectId}/containers/{containerId}
            int projects = indexOf(parts, "projects");
            int containers = indexOf(parts, "containers");
            if (projects >= 0 && projects + 1 < parts.length) {
                attributes.put(ATTR_PROJECT_ID, parts[projects + 1]);
            }
            if (containers >= 0 && containers + 1 < parts.length) {
                attributes.put(ATTR_CONTAINER_ID, parts[containers + 1]);
            }
            return true;
        }

        private static int indexOf(String[] parts, String needle) {
            for (int i = 0; i < parts.length; i++) {
                if (needle.equals(parts[i])) {
                    return i;
                }
            }
            return -1;
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
