package com.rehab.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private Long id;
    private String name;
    private String role;
    private String token;
    private Long accessTokenExpiresIn;
}
