package com.rehab.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RehabCourse {
    private Long id;
    private String name;
    private String category;
    private String suitableCrowd;
    private String trainingGoal;
    private Integer durationMinutes;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
