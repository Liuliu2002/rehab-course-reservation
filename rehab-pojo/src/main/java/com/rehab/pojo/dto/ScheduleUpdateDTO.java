package com.rehab.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class ScheduleUpdateDTO {
    @NotNull(message = "start time is required")
    @Future(message = "start time must be in the future")
    private LocalDateTime startTime;
    @NotNull(message = "end time is required")
    @Future(message = "end time must be in the future")
    private LocalDateTime endTime;
}
