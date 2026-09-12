package com.rehab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rehab.mq.AppointmentMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RabbitMqConfigTest {
    @Test
    void jsonConverterRoundTripsAppointmentMessage() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MessageConverter converter = new RabbitMqConfig().rabbitMessageConverter(objectMapper);
        AppointmentMessage source = new AppointmentMessage(
                1L, "R1", 2L, 3L, 4L, 5L,
                new BigDecimal("99.00"), LocalDateTime.of(2026, 9, 8, 12, 0));

        Message encoded = converter.toMessage(source, new MessageProperties());
        Object decoded = converter.fromMessage(encoded);

        assertInstanceOf(AppointmentMessage.class, decoded);
        assertEquals(source, decoded);
    }
}
