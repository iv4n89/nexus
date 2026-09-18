package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.application.terminal.TerminalSessionManager;
import com.ivan.nexus.domain.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "nexus.terminal.enabled", havingValue = "true")
public class ContainerTerminalWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ContainerTerminalWebSocketHandler.class);
    private static final String ATTR_SESSION_ID = "nexus.terminal.sessionId";

    private final TerminalSessionManager sessions;

    public ContainerTerminalWebSocketHandler(TerminalSessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Authentication auth = requireAdmin(session);
        String projectId = (String) session.getAttributes().get(TerminalWebSocketConfig.ATTR_PROJECT_ID);
        String containerId = (String) session.getAttributes().get(TerminalWebSocketConfig.ATTR_CONTAINER_ID);
        if (projectId == null || projectId.isBlank() || containerId == null || containerId.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("project and container required"));
            throw new IllegalStateException("Missing project/container path variables");
        }
        String username = auth.getName();
        String clientIp = (String) session.getAttributes().get(TerminalWebSocketConfig.ATTR_CLIENT_IP);
        try {
            TerminalSessionManager.TerminalSession terminal =
                    sessions.openContainer(username, clientIp, projectId, containerId);
            session.getAttributes().put(ATTR_SESSION_ID, terminal.id());
            sessions.onOutput(terminal.id(), chunk -> sendBinary(session, chunk));
            session.sendMessage(new TextMessage(
                    "{\"type\":\"ready\",\"sessionId\":\"" + terminal.id()
                            + "\",\"projectId\":\"" + projectId
                            + "\",\"containerId\":\"" + containerId + "\"}"));
        } catch (DomainException ex) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(ex.getMessage()));
            throw ex;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessions.write(sessionId(session), message.getPayload().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        sessions.write(sessionId(session), data);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID sessionId = (UUID) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) {
            sessions.close(sessionId, "websocket-closed");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Container terminal websocket error: {}", exception.toString());
        UUID sessionId = (UUID) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) {
            sessions.close(sessionId, "transport-error");
        }
    }

    private static UUID sessionId(WebSocketSession session) {
        UUID id = (UUID) session.getAttributes().get(ATTR_SESSION_ID);
        if (id == null) {
            throw new IllegalStateException("Terminal session not established");
        }
        return id;
    }

    private static void sendBinary(WebSocketSession session, byte[] chunk) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(chunk));
            }
        } catch (IOException e) {
            log.debug("Failed to push terminal output: {}", e.toString());
        }
    }

    private static Authentication requireAdmin(WebSocketSession session) throws IOException {
        Authentication auth = (Authentication) session.getAttributes()
                .get(TerminalWebSocketConfig.ATTR_AUTHENTICATION);
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !hasAdmin(auth)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("ADMIN required"));
            throw new SecurityException("Terminal requires ADMIN role");
        }
        return auth;
    }

    private static boolean hasAdmin(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
