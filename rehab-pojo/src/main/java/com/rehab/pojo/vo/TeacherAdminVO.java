package com.rehab.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员教师名册视图，不返回密码哈希。
 */
@Data
public class TeacherAdminVO {
    private Long id;
    private String name;
    private String phone;
    private String specialty;
    private String introduction;
    private Integer status;
    private String role;
    private LocalDateTime createTime;
}
