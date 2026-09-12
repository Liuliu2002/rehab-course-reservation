package com.rehab.service.impl;

import com.rabbitmq.client.Channel;
import com.rehab.config.RabbitMqConfig;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.mq.AppointmentMessage;
import com.rehab.pojo.entity.Appointment;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 预约消费者。主队列失败后由容器最多重试 5 次，仍失败的消息进入死信队列。
 * 消费使用预约 ID 幂等，并在数据库事务提交后才手动 ACK。
 */
@Slf4j
@Component
public class AppointmentRabbitConsumer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AppointmentMapper appointmentMapper;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RedisCacheClient redisCacheClient;

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_QUEUE)
    public void consume(AppointmentMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            Boolean success = transactionTemplate.execute(status -> createAppointment(message));
            setSubmitStatus(message.getAppointmentId(), Boolean.TRUE.equals(success) ? "SUCCESS" : "FAILED");
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.error("failed to consume appointment message, appointmentId={}",
                    message.getAppointmentId(), exception);
            // 抛出异常让 Spring AMQP 的重试拦截器执行重试；耗尽后拒绝并路由到 DLQ。
            throw new AmqpException("failed to consume appointment message", exception);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_DEAD_LETTER_QUEUE)
    public void consumeDeadLetter(AppointmentMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            Appointment persisted = appointmentMapper.getById(message.getAppointmentId());
            if (persisted != null) {
                setSubmitStatus(message.getAppointmentId(), "SUCCESS");
            } else {
                setSubmitStatus(message.getAppointmentId(), "FAILED");
                syncScheduleStock(message.getScheduleId(), message.getTeacherId());
            }
            log.error("appointment message moved to dead-letter queue, appointmentId={}",
                    message.getAppointmentId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.error("failed to compensate dead-letter appointment, appointmentId={}",
                    message.getAppointmentId(), exception);
            // DLQ 处理失败时保留消息，等待稍后重新投递。
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private boolean createAppointment(AppointmentMessage message) {
        if (appointmentMapper.getById(message.getAppointmentId()) != null) {
            return true;
        }

        Integer occupied = scheduleMapper.occupyAvailable(message.getScheduleId());
        if (occupied == null || occupied == 0) {
            syncScheduleStock(message.getScheduleId(), message.getTeacherId());
            return false;
        }

        Appointment appointment = buildAppointment(message);
        try {
            appointmentMapper.insert(appointment);
            return true;
        } catch (DuplicateKeyException exception) {
            if (appointmentMapper.getById(message.getAppointmentId()) != null) {
                return true;
            }
            throw exception;
        }
    }

    private Appointment buildAppointment(AppointmentMessage message) {
        Appointment appointment = new Appointment();
        appointment.setId(message.getAppointmentId());
        appointment.setAppointmentNo(message.getAppointmentNo());
        appointment.setStudentId(message.getStudentId());
        appointment.setTeacherId(message.getTeacherId());
        appointment.setCourseId(message.getCourseId());
        appointment.setScheduleId(message.getScheduleId());
        appointment.setAmount(message.getAmount());
        appointment.setStatus(Appointment.PENDING_CONFIRM);
        appointment.setCreateTime(message.getCreateTime());
        appointment.setUpdateTime(message.getCreateTime());
        return appointment;
    }

    private void setSubmitStatus(Long appointmentId, String status) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.APPOINTMENT_STATUS_KEY + appointmentId,
                status,
                RedisKeys.APPOINTMENT_STATUS_TTL_HOURS,
                TimeUnit.HOURS);
    }

    private void syncScheduleStock(Long scheduleId, Long teacherId) {
        TeacherSchedule schedule = scheduleMapper.getById(scheduleId);
        String stock = schedule != null && Integer.valueOf(1).equals(schedule.getStatus()) ? "1" : "0";
        stringRedisTemplate.opsForValue().set(
                RedisKeys.APPOINTMENT_STOCK_KEY + scheduleId, stock, 7, TimeUnit.DAYS);
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + teacherId);
    }
}
