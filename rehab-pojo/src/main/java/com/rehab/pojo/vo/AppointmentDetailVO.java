package com.rehab.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面向预约列表的聚合视图，避免前端只能展示内部 ID。
 */
@Data
public class AppointmentDetailVO {
    private Long id;
    private String appointmentNo;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private Long courseId;
    private String courseName;
    private String courseCategory;
    private Long scheduleId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private BigDecimal amount;
    private String cancelReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
