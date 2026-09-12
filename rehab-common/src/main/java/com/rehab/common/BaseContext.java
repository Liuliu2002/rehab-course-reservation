package com.rehab.common;

/**
 * 当前请求的用户上下文。
 * ThreadLocal 让 Controller/Service 不必层层传递 userId，但在线程池环境中必须在请求结束时 remove。
 */
public class BaseContext {
    // ThreadLocal 保存当前请求的登录用户信息，避免在每个 service 方法里反复传 userId。
    // 请求结束后必须 remove，防止 Tomcat 线程复用导致用户信息串到下一次请求。
    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);
    }

    public static void setCurrentRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static Long getCurrentId() {
        return CURRENT_ID.get();
    }

    public static String getCurrentRole() {
        return CURRENT_ROLE.get();
    }

    public static void setCurrentSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public static void remove() {
        CURRENT_ID.remove();
        CURRENT_ROLE.remove();
        CURRENT_SESSION_ID.remove();
    }
}
