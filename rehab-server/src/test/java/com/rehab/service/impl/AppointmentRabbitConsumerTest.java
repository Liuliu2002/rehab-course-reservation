package com.rehab.service.impl;

import com.rabbitmq.client.Channel;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.mq.AppointmentMessage;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentRabbitConsumerTest {
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private RedisCacheClient redisCacheClient;
    @Mock private Channel channel;
    @InjectMocks private AppointmentRabbitConsumer consumer;

    @Test
    void acknowledgesOnlyAfterSuccessfulProcessing() throws Exception {
        AppointmentMessage message = message();
        when(transactionTemplate.execute(any())).thenReturn(Boolean.TRUE);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        consumer.consume(message, channel, 17L);

        verify(valueOperations).set(
                RedisKeys.APPOINTMENT_STATUS_KEY + message.getAppointmentId(),
                "SUCCESS",
                RedisKeys.APPOINTMENT_STATUS_TTL_HOURS,
                TimeUnit.HOURS);
        verify(channel).basicAck(17L, false);
    }

    @Test
    void leavesMessageUnackedWhenProcessingFails() throws Exception {
        AppointmentMessage message = message();
        when(transactionTemplate.execute(any())).thenThrow(new IllegalStateException("db unavailable"));

        assertThrows(AmqpException.class, () -> consumer.consume(message, channel, 18L));

        verify(channel, never()).basicAck(18L, false);
    }

    private AppointmentMessage message() {
        return new AppointmentMessage(
                1L, "R1", 2L, 3L, 4L, 5L,
                new BigDecimal("99.00"), LocalDateTime.of(2026, 9, 8, 12, 0));
    }
}
