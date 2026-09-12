package com.rehab.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class LoginDTO {
    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "phone format is invalid")
    private String phone;
    @NotBlank(message = "password is required")
    private String password;
}
