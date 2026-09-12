package com.rehab.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherSchedule {
    private Long id;
    private Long teacherId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createTime;
}
