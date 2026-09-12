package com.rehab.websocket;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 教师端预约通知通道。
 * 握手阶段已经把 JWT 中的 teacherId 写入 session attributes，推送时按教师 ID 定向发送，
 * 避免将预约消息广播给无关教师。
 */
public class AppointmentWebSocket extends TextWebSocketHandler implements SubProtocolCapable {
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.put(session.getId(), session);
    }

    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList("rehab");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
    }

    public static void broadcastToTeacher(Long teacherId, String message) {
        SESSIONS.values().stream()
                .filter(session -> teacherId.equals(session.getAttributes().get("teacherId")))
                .forEach(session -> sendIfAuthorized(session, message));
    }

    public static void closeAuthSession(String authSessionId) {
        SESSIONS.values().stream()
                .filter(session -> authSessionId != null
                        && authSessionId.equals(session.getAttributes().get("authSessionId")))
                .forEach(AppointmentWebSocket::closeQuietly);
    }

    public static void closeTeacherSessions(Long teacherId) {
        SESSIONS.values().stream()
                .filter(session -> teacherId.equals(session.getAttributes().get("teacherId")))
                .forEach(AppointmentWebSocket::closeQuietly);
    }

    private static void sendIfAuthorized(WebSocketSession session, String message) {
        Object expiresAt = session.getAttributes().get("accessExpiresAt");
        if (!session.isOpen() || !(expiresAt instanceof Long) || (Long) expiresAt <= System.currentTimeMillis()) {
            closeQuietly(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception ignored) {
            closeQuietly(session);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        SESSIONS.remove(session.getId());
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception ignored) {
        }
    }
}
