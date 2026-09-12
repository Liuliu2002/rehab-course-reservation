package com.rehab.controller.admin;

import com.rehab.common.Result;
import com.rehab.pojo.dto.CourseDTO;
import com.rehab.pojo.entity.RehabCourse;
import com.rehab.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/admin/course")
public class AdminCourseController {
    @Autowired
    private CourseService courseService;

    @PostMapping
    public Result<Void> save(@Valid @RequestBody CourseDTO courseDTO) {
        courseService.save(courseDTO);
        return Result.success();
    }

    @GetMapping
    public Result<List<RehabCourse>> list() {
        return Result.success(courseService.listAll());
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        courseService.updateStatus(id, status);
        return Result.success();
    }
}
