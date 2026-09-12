package com.rehab.interceptor;

import com.rehab.common.BaseContext;
import com.rehab.common.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * REST 接口身份认证与轻量 RBAC 拦截器。
 * JWT 负责证明“用户 ID + 登录时角色”，BaseContext 负责把身份传递到本次请求的业务层。
 * 管理员才能维护课程和教师；教师/管理员可处理教师端预约；学生只能访问学生端接口。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        if (token == null || token.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Claims claims;
        try {
            claims = JwtUtil.parseToken(token);
            if (!"access".equals(claims.get("type")) || claims.get("sid") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        String role = claims.get("role").toString();
        if (!hasPermission(request.getRequestURI(), role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        // 解析出的用户 ID 放入 ThreadLocal，业务层可以直接通过 BaseContext 获取当前登录人。
        BaseContext.setCurrentId(Long.valueOf(claims.get("id").toString()));
        BaseContext.setCurrentRole(role);
        BaseContext.setCurrentSessionId(claims.get("sid").toString());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.remove();
    }

    private boolean hasPermission(String uri, String role) {
        if (uri.startsWith("/admin/teacher") || uri.startsWith("/admin/course")) {
            return "admin".equals(role);
        }
        // 简单 RBAC：教师只能访问 /admin/**，学生只能访问 /student/**。
        if (uri.startsWith("/admin/")) {
            return "teacher".equals(role) || "admin".equals(role);
        }
        if (uri.startsWith("/student/")) {
            return "student".equals(role);
        }
        return true;
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        // 暂时兼容旧版前端使用的 token 请求头。
        return request.getHeader("token");
    }
}
