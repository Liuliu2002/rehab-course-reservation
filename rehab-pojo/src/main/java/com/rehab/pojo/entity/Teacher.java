package com.rehab.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class Teacher {
    private Long id;
    @NotBlank(message = "teacher name is required")
    private String name;
    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "phone format is invalid")
    private String phone;
    @NotBlank(message = "password is required")
    private String password;
    private String specialty;
    private String introduction;
    private Integer status;
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
