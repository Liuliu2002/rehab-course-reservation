package com.rehab.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;

@Data
public class ScheduleDTO {
    @NotNull(message = "teacherId is required")
    private Long teacherId;
    @NotNull(message = "start time is required")
    @Future(message = "start time must be in the future")
    private LocalDateTime startTime;
    @NotNull(message = "end time is required")
    @Future(message = "end time must be in the future")
    private LocalDateTime endTime;
}
