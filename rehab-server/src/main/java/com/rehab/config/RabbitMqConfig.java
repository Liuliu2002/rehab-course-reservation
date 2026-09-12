package com.rehab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String APPOINTMENT_EXCHANGE = "rehab.appointment.exchange";
    public static final String APPOINTMENT_QUEUE = "rehab.appointment.queue";
    public static final String APPOINTMENT_ROUTING_KEY = "appointment.created";
    public static final String APPOINTMENT_DEAD_LETTER_EXCHANGE = "rehab.appointment.dlx";
    public static final String APPOINTMENT_DEAD_LETTER_QUEUE = "rehab.appointment.dead-letter.queue";
    public static final String APPOINTMENT_DEAD_LETTER_ROUTING_KEY = "appointment.dead-letter";

    @Bean
    public DirectExchange appointmentExchange() {
        return new DirectExchange(APPOINTMENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange appointmentDeadLetterExchange() {
        return new DirectExchange(APPOINTMENT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue appointmentQueue() {
        return QueueBuilder.durable(APPOINTMENT_QUEUE)
                .deadLetterExchange(APPOINTMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(APPOINTMENT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue appointmentDeadLetterQueue() {
        return QueueBuilder.durable(APPOINTMENT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding appointmentBinding() {
        return BindingBuilder.bind(appointmentQueue())
                .to(appointmentExchange())
                .with(APPOINTMENT_ROUTING_KEY);
    }

    @Bean
    public Binding appointmentDeadLetterBinding() {
        return BindingBuilder.bind(appointmentDeadLetterQueue())
                .to(appointmentDeadLetterExchange())
                .with(APPOINTMENT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
