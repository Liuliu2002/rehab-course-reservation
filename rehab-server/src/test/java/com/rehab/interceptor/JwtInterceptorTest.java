package com.rehab.interceptor;

import com.rehab.common.BaseContext;
import com.rehab.common.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {
    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    @Test
    void validAccessTokenIsAcceptedWithoutServerSessionLookup() {
        String token = JwtUtil.createAccessToken(7L, "student", "session-1", 60_000L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/student/course");

        assertTrue(new JwtInterceptor().preHandle(request, response, new Object()));
        assertEquals(7L, BaseContext.getCurrentId());
        assertEquals("student", BaseContext.getCurrentRole());
        assertEquals("session-1", BaseContext.getCurrentSessionId());
    }
}
