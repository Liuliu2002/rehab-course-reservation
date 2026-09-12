package com.rehab.service.impl;

import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.mapper.TeacherMapper;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.TeacherScheduleDetailVO;
import com.rehab.utils.RedisCacheClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private RedisCacheClient redisCacheClient;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private TeacherMapper teacherMapper;
    @Mock private AppointmentMapper appointmentMapper;
    @InjectMocks private ScheduleServiceImpl service;

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
    void listsOnlyCurrentTeachersSchedules() {
        List<TeacherScheduleDetailVO> expected = Collections.singletonList(new TeacherScheduleDetailVO());
        when(scheduleMapper.listDetailsByTeacher(7L)).thenReturn(expected);

        List<TeacherScheduleDetailVO> actual = service.listForCurrentTeacher();

        assertSame(expected, actual);
        verify(scheduleMapper).listDetailsByTeacher(7L);
    }

    @Test
    void teacherCannotDeleteAnotherTeachersSchedule() {
        TeacherSchedule schedule = new TeacherSchedule();
        schedule.setId(21L);
        schedule.setTeacherId(8L);
        schedule.setStatus(3);
        when(scheduleMapper.getById(21L)).thenReturn(schedule);

        assertThrows(BusinessException.class, () -> service.delete(21L));

        verify(scheduleMapper, never()).deleteDisabled(21L);
    }

    @Test
    void scheduleWithAppointmentHistoryCannotBeDeleted() {
        TeacherSchedule schedule = new TeacherSchedule();
        schedule.setId(22L);
        schedule.setTeacherId(7L);
        schedule.setStatus(3);
        when(scheduleMapper.getById(22L)).thenReturn(schedule);
        when(appointmentMapper.countBySchedule(22L)).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.delete(22L));

        verify(scheduleMapper, never()).deleteDisabled(22L);
    }
}
