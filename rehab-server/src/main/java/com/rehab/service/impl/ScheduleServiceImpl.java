package com.rehab.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.TeacherMapper;
import com.rehab.pojo.dto.ScheduleDTO;
import com.rehab.pojo.dto.ScheduleUpdateDTO;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.TeacherScheduleDetailVO;
import com.rehab.service.ScheduleService;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 教师排班服务。
 *
 * <p>创建排班时先锁定教师行，再检查时间交集并插入排班。锁教师行的目的不是读取教师，
 * 而是让同一教师的并发排班请求串行执行，关闭“两个请求同时查到无冲突”的并发窗口。</p>
 */
@Service
@Slf4j
public class ScheduleServiceImpl implements ScheduleService {
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private RedisCacheClient redisCacheClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private TeacherMapper teacherMapper;
    @Autowired
    private AppointmentMapper appointmentMapper;

    @Override
    @Transactional
    public void save(ScheduleDTO scheduleDTO) {
        if ("teacher".equals(BaseContext.getCurrentRole())
                && !BaseContext.getCurrentId().equals(scheduleDTO.getTeacherId())) {
            throw BusinessException.forbidden("teachers can only create their own schedules");
        }
        if (!scheduleDTO.getEndTime().isAfter(scheduleDTO.getStartTime())) {
            throw BusinessException.badRequest("end time must be after start time");
        }
        if (teacherMapper.lockActiveById(scheduleDTO.getTeacherId()) == null) {
            throw BusinessException.badRequest("teacher does not exist or is disabled");
        }
        Integer conflict = scheduleMapper.countTimeConflict(
                scheduleDTO.getTeacherId(), scheduleDTO.getStartTime(), scheduleDTO.getEndTime());
        if (conflict != null && conflict > 0) {
            throw BusinessException.conflict("teacher schedule time conflicts");
        }

        TeacherSchedule schedule = new TeacherSchedule();
        BeanUtils.copyProperties(scheduleDTO, schedule);
        schedule.setStatus(1);
        schedule.setCreateTime(LocalDateTime.now());
        scheduleMapper.insert(schedule);
        stringRedisTemplate.opsForValue().set(RedisKeys.APPOINTMENT_STOCK_KEY + schedule.getId(), "1", 7, TimeUnit.DAYS);
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + schedule.getTeacherId());
    }

    @Override
    public List<TeacherSchedule> listAvailable(Long teacherId) {
        // MySQL 状态负责持久化正确性，Redis 的 0/1 库存负责过滤异步落库期间的临时占用。
        List<TeacherSchedule> schedules = redisCacheClient.queryList(
                RedisKeys.CACHE_SCHEDULE_KEY + teacherId,
                new TypeReference<List<TeacherSchedule>>() {
                },
                () -> scheduleMapper.listAvailableByTeacher(teacherId));
        return schedules.stream()
                .filter(this::hasAvailableStock)
                .collect(Collectors.toList());
    }

    @Override
    public List<TeacherScheduleDetailVO> listForCurrentTeacher() {
        return scheduleMapper.listDetailsByTeacher(BaseContext.getCurrentId());
    }

    @Override
    @Transactional
    public void update(Long id, ScheduleUpdateDTO updateDTO) {
        TeacherSchedule schedule = getOperableSchedule(id);
        ensureNotOccupiedOrProcessing(schedule);
        validateTimeRange(updateDTO.getStartTime(), updateDTO.getEndTime());
        if (scheduleMapper.countTimeConflictExcluding(
                schedule.getTeacherId(), id, updateDTO.getStartTime(), updateDTO.getEndTime()) > 0) {
            throw BusinessException.conflict("teacher schedule time conflicts");
        }
        if (scheduleMapper.updateTime(id, updateDTO.getStartTime(), updateDTO.getEndTime()) != 1) {
            throw BusinessException.conflict("occupied schedule cannot be modified");
        }
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + schedule.getTeacherId());
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 3)) {
            throw BusinessException.badRequest("schedule status must be 1 or 3");
        }
        TeacherSchedule schedule = getOperableSchedule(id);
        if (schedule.getStatus().equals(status)) {
            return;
        }
        ensureNotOccupiedOrProcessing(schedule);
        if (status == 1) {
            if (!schedule.getStartTime().isAfter(LocalDateTime.now())) {
                throw BusinessException.conflict("past schedule cannot be enabled");
            }
            Integer conflict = scheduleMapper.countTimeConflictExcluding(
                    schedule.getTeacherId(), id, schedule.getStartTime(), schedule.getEndTime());
            if (conflict != null && conflict > 0) {
                throw BusinessException.conflict("teacher schedule time conflicts");
            }
        }
        if (scheduleMapper.updateStatusIfCurrent(id, status, schedule.getStatus()) != 1) {
            throw BusinessException.conflict("schedule status has changed, please refresh");
        }
        String stockKey = RedisKeys.APPOINTMENT_STOCK_KEY + id;
        if (status == 1) {
            stringRedisTemplate.opsForValue().set(stockKey, "1", 7, TimeUnit.DAYS);
        } else {
            stringRedisTemplate.delete(stockKey);
        }
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + schedule.getTeacherId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TeacherSchedule schedule = getOperableSchedule(id);
        if (schedule.getStatus() != 3) {
            throw BusinessException.conflict("schedule must be disabled before deletion");
        }
        if (appointmentMapper.countBySchedule(id) > 0) {
            throw BusinessException.conflict("schedule with appointment history cannot be deleted");
        }
        if (scheduleMapper.deleteDisabled(id) != 1) {
            throw BusinessException.conflict("schedule cannot be deleted");
        }
        stringRedisTemplate.delete(RedisKeys.APPOINTMENT_STOCK_KEY + id);
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + schedule.getTeacherId());
    }

    private TeacherSchedule getOperableSchedule(Long id) {
        TeacherSchedule schedule = scheduleMapper.getById(id);
        if (schedule == null) {
            throw BusinessException.badRequest("schedule does not exist");
        }
        if ("teacher".equals(BaseContext.getCurrentRole())
                && !BaseContext.getCurrentId().equals(schedule.getTeacherId())) {
            throw BusinessException.forbidden("teachers can only manage their own schedules");
        }
        return schedule;
    }

    private void ensureNotOccupiedOrProcessing(TeacherSchedule schedule) {
        if (schedule.getStatus() == 2) {
            throw BusinessException.conflict("occupied schedule cannot be changed");
        }
        String stock = stringRedisTemplate.opsForValue().get(
                RedisKeys.APPOINTMENT_STOCK_KEY + schedule.getId());
        if ("0".equals(stock)) {
            throw BusinessException.conflict("appointment is being processed for this schedule");
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw BusinessException.badRequest("end time must be after start time");
        }
    }

    private boolean hasAvailableStock(TeacherSchedule schedule) {
        String stock = stringRedisTemplate.opsForValue().get(RedisKeys.APPOINTMENT_STOCK_KEY + schedule.getId());
        if (stock == null) {
            return true;
        }
        try {
            return Integer.parseInt(stock) > 0;
        } catch (NumberFormatException e) {
            log.warn("invalid redis stock value, scheduleId={}, stock={}", schedule.getId(), stock);
            return false;
        }
    }
}
