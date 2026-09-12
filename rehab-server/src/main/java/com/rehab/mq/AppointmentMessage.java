package com.rehab.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMessage {
    private Long appointmentId;
    private String appointmentNo;
    private Long studentId;
    private Long teacherId;
    private Long courseId;
    private Long scheduleId;
    private BigDecimal amount;
    private LocalDateTime createTime;
}
