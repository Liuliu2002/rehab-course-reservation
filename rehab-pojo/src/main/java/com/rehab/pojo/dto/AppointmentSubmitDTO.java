package com.rehab.pojo.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class AppointmentSubmitDTO {
    @NotNull(message = "teacherId is required")
    private Long teacherId;
    @NotNull(message = "courseId is required")
    private Long courseId;
    @NotNull(message = "scheduleId is required")
    private Long scheduleId;
}
