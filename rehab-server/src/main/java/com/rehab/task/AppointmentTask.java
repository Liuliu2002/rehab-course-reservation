package com.rehab.task;

import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.pojo.entity.Appointment;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 预约超时补偿任务。
 * 定时扫描只是触发器，真正的并发安全依赖 updateStatusIfCurrent 的条件更新：
 * 多实例同时扫描同一预约时，也只有一个实例可以把待确认状态改为已取消。
 */
@Component
public class AppointmentTask {
    @Autowired
    private AppointmentMapper appointmentMapper;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisCacheClient redisCacheClient;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void cancelTimeoutAppointments() {
        // 每 5 分钟扫描一次 30 分钟未确认的预约，避免学生一直占着排班名额。
        List<Appointment> appointments = appointmentMapper.listTimeoutPending(LocalDateTime.now().minusMinutes(30));
        for (Appointment appointment : appointments) {
            String reason = "teacher did not confirm in time, cancelled automatically";
            int updated = appointmentMapper.updateStatusIfCurrent(
                    appointment.getId(), Appointment.CANCELLED, reason, Appointment.PENDING_CONFIRM);
            if (updated != 1) {
                continue;
            }
            // 超时取消后释放 MySQL 排班状态和 Redis 库存，并删除排班缓存。
            scheduleMapper.releaseOccupied(appointment.getScheduleId());
            stringRedisTemplate.opsForValue().set(RedisKeys.APPOINTMENT_STOCK_KEY + appointment.getScheduleId(), "1", 7, TimeUnit.DAYS);
            redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + appointment.getTeacherId());
        }
    }
}
