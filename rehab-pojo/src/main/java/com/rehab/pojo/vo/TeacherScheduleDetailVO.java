package com.rehab.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师排班视图：把排班时间、占用状态和当前有效预约放在同一条记录中。
 */
@Data
public class TeacherScheduleDetailVO {
    private Long scheduleId;
    private Long teacherId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer scheduleStatus;
    private Long appointmentId;
    private String appointmentNo;
    private Integer appointmentStatus;
    private Long studentId;
    private Long courseId;
    private String courseName;
    private String courseCategory;
}
