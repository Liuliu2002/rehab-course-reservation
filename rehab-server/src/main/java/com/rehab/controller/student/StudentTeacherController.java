package com.rehab.controller.student;

import com.rehab.common.Result;
import com.rehab.pojo.vo.TeacherVO;
import com.rehab.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/student/teacher")
public class StudentTeacherController {
    @Autowired
    private TeacherService teacherService;

    @GetMapping
    public Result<List<TeacherVO>> list() {
        return Result.success(teacherService.listEnabled());
    }
}
