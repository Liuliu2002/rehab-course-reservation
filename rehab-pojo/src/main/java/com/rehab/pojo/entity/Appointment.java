package com.rehab.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约聚合的持久化实体。
 *
 * <p>合法状态流转：</p>
 * <pre>
 * 待确认(1) -> 已确认(2) -> 已完成(3)
 * 待确认(1) -> 已拒绝(5)
 * 待确认(1) / 已确认(2) -> 已取消(4)
 * </pre>
 */
@Data
public class Appointment {
    public static final Integer PENDING_CONFIRM = 1;
    public static final Integer CONFIRMED = 2;
    public static final Integer COMPLETED = 3;
    public static final Integer CANCELLED = 4;
    public static final Integer REJECTED = 5;

    private Long id;
    private String appointmentNo;
    private Long studentId;
    private Long teacherId;
    private Long courseId;
    private Long scheduleId;
    private Integer status;
    private BigDecimal amount;
    private String cancelReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
