package com.rehab.service.impl;

import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.CourseMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.pojo.dto.AppointmentDecisionDTO;
import com.rehab.pojo.entity.Appointment;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisIdWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RedisIdWorker redisIdWorker;
    @Mock private RedisCacheClient redisCacheClient;
    @InjectMocks private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        BaseContext.setCurrentRole("teacher");
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    @Test
    void confirmUsesConditionalStateTransition() {
        when(appointmentMapper.getById(11L)).thenReturn(appointment(11L, 8L, 7L, 21L));
        when(appointmentMapper.updateStatusIfCurrent(
                11L, Appointment.CONFIRMED, null, Appointment.PENDING_CONFIRM)).thenReturn(1);

        service.confirm(11L);

        verify(appointmentMapper).updateStatusIfCurrent(
                11L, Appointment.CONFIRMED, null, Appointment.PENDING_CONFIRM);
    }

    @Test
    void confirmReportsConcurrentStateChange() {
        when(appointmentMapper.getById(11L)).thenReturn(appointment(11L, 8L, 7L, 21L));
        when(appointmentMapper.updateStatusIfCurrent(any(), any(), any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirm(11L));
    }

    @Test
    void failedCancelDoesNotReleaseScheduleOrRedisStock() {
        BaseContext.setCurrentId(8L);
        AppointmentDecisionDTO decision = new AppointmentDecisionDTO();
        decision.setAppointmentId(11L);
        decision.setReason("conflict");
        when(appointmentMapper.getById(11L)).thenReturn(appointment(11L, 8L, 7L, 21L));
        when(appointmentMapper.cancelIfActive(11L, "conflict")).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.cancel(decision));

        verify(scheduleMapper, never()).releaseOccupied(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    private Appointment appointment(Long id, Long studentId, Long teacherId, Long scheduleId) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setStudentId(studentId);
        appointment.setTeacherId(teacherId);
        appointment.setScheduleId(scheduleId);
        appointment.setStatus(Appointment.PENDING_CONFIRM);
        return appointment;
    }
}
