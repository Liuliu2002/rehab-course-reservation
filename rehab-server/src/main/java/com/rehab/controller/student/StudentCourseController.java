package com.rehab.controller.student;

import com.rehab.common.Result;
import com.rehab.pojo.entity.RehabCourse;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.service.CourseService;
import com.rehab.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/student/course")
public class StudentCourseController {
    @Autowired
    private CourseService courseService;
    @Autowired
    private ScheduleService scheduleService;

    @GetMapping
    public Result<List<RehabCourse>> list() {
        return Result.success(courseService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<RehabCourse> detail(@PathVariable Long id) {
        return Result.success(courseService.detail(id));
    }

    @GetMapping("/schedule")
    public Result<List<TeacherSchedule>> schedules(@RequestParam Long teacherId) {
        return Result.success(scheduleService.listAvailable(teacherId));
    }
}
