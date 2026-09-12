package com.rehab.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherVO {
    private Long id;
    private String name;
    private String specialty;
    private String introduction;
}
