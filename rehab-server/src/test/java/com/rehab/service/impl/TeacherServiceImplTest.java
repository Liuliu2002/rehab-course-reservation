package com.rehab.service.impl;

import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.mapper.TeacherMapper;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import com.rehab.service.AuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceImplTest {
    @Mock private TeacherMapper teacherMapper;
    @Mock private RedisCacheClient redisCacheClient;
    @Mock private AuthTokenService authTokenService;
    @InjectMocks private TeacherServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentRole("admin");
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    @Test
    void currentAdministratorCannotDisableSelf() {
        assertThrows(BusinessException.class, () -> service.updateStatus(1L, 0));

        verify(teacherMapper, never()).updateStatus(1L, 0);
    }

    @Test
    void updatingTeacherStatusInvalidatesPublicTeacherCache() {
        when(teacherMapper.updateStatus(2L, 0)).thenReturn(1);

        service.updateStatus(2L, 0);

        verify(redisCacheClient).delete(RedisKeys.CACHE_TEACHER_LIST_KEY);
        verify(authTokenService).revokeUserSessions(2L, "teacher");
        verify(authTokenService).revokeUserSessions(2L, "admin");
    }
}
