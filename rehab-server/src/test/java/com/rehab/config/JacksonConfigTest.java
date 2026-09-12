package com.rehab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehab.pojo.entity.Appointment;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigTest {
    @Test
    void serializesLongIdsAsStringsForJavaScriptClients() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longIdCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();
        Appointment appointment = new Appointment();
        appointment.setId(359072713488203778L);

        String json = objectMapper.writeValueAsString(appointment);

        assertTrue(json.contains("\"id\":\"359072713488203778\""), json);
    }
}
