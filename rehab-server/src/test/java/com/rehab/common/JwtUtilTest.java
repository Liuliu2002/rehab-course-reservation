package com.rehab.common;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {
    @Test
    void accessTokenContainsSessionAndConfiguredExpiry() {
        long before = System.currentTimeMillis();
        String token = JwtUtil.createAccessToken(7L, "student", "session-1", 60_000L);
        Claims claims = JwtUtil.parseToken(token);

        assertEquals("access", claims.get("type"));
        assertEquals("session-1", claims.get("sid"));
        assertEquals("student", claims.get("role"));
        assertTrue(claims.getExpiration().getTime() >= before + 59_000L);
        assertTrue(claims.getExpiration().getTime() <= System.currentTimeMillis() + 60_000L);
    }
}
