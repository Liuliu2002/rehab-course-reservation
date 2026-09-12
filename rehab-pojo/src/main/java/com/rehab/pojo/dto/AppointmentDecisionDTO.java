package com.rehab.pojo.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class AppointmentDecisionDTO {
    @NotNull(message = "appointmentId is required")
    private Long appointmentId;
    @Size(max = 255, message = "reason is too long")
    private String reason;
}
