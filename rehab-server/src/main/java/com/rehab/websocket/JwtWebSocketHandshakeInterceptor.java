package com.rehab.websocket;

import com.rehab.common.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * WebSocket 握手鉴权。
 * 浏览器 WebSocket API 不能自定义 Authorization 请求头，因此前端通过子协议头携带 JWT；
 * 服务端只允许 teacher/admin 建立预约通知通道，并将用户 ID 放入会话属性。
 */
@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        String token = protocols == null ? null : Arrays.stream(protocols.split(","))
                .map(String::trim)
                .filter(value -> !"rehab".equals(value))
                .findFirst()
                .orElse(null);
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            if (!"access".equals(claims.get("type")) || claims.get("sid") == null) {
                return false;
            }
            String role = String.valueOf(claims.get("role"));
            if (!"teacher".equals(role) && !"admin".equals(role)) {
                return false;
            }
            attributes.put("teacherId", Long.valueOf(claims.get("id").toString()));
            attributes.put("authSessionId", claims.get("sid").toString());
            attributes.put("accessExpiresAt", claims.getExpiration().getTime());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
