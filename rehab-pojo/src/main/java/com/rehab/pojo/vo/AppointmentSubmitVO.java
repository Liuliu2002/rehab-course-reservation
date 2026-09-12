package com.rehab.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppointmentSubmitVO {
    private Long appointmentId;
    private String appointmentNo;
    private String status;
}
