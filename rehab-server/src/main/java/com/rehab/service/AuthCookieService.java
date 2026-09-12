package com.rehab.service;

import com.rehab.config.AuthProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

@Component
public class AuthCookieService {
    // Cookie名称：浏览器中存储刷新令牌的cookie key
    public static final String REFRESH_COOKIE_NAME = "rehab_refresh";

    // 读取配置文件认证相关配置，来自 AuthProperties（配置类，读取application.yml/properties）
    private final AuthProperties properties;

    // 构造函数注入，Spring自动注入AuthProperties配置Bean
    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 写入刷新令牌Cookie，返回给浏览器
     * @param response Http响应对象
     * @param refreshToken 刷新令牌字符串
     * @param expiresInSeconds cookie有效期，单位秒
     */
    public void writeRefreshCookie(HttpServletResponse response, String refreshToken, long expiresInSeconds) {
        // SET‑COOKIE 响应头，告诉浏览器设置cookie
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(refreshToken, Duration.ofSeconds(expiresInSeconds)).toString());
    }

    /**
     * 清除刷新令牌Cookie：设置空值，有效期0，浏览器立刻删除该cookie。用于登出接口
     */
    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /**
     * 私有方法，构建ResponseCookie对象，统一cookie安全参数
     * @param value cookie值（refreshToken / 空字符串）
     * @param maxAge 有效期
     * @return 构建好的cookie
     */
    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)        // ✅ 关键：禁止JS读取Cookie，防御XSS攻击，前端拿不到refreshToken
                .secure(properties.isRefreshCookieSecure()) // secure开关：true=只在HTTPS下传输cookie；http环境不会携带
                .sameSite("Strict")    // SameSite=Strict：严格模式，跨站请求不会带上这个cookie，防御CSRF
                .path("/")             // cookie作用路径：整个网站所有路径都携带此cookie
                .maxAge(maxAge)        // cookie存活时间
                .build();
    }
}
