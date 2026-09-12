package com.rehab.controller.admin;

import com.rehab.common.Result;
import com.rehab.pojo.dto.ScheduleDTO;
import com.rehab.pojo.dto.ScheduleUpdateDTO;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.TeacherScheduleDetailVO;
import com.rehab.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/admin/schedule")
public class AdminScheduleController {
    @Autowired
    private ScheduleService scheduleService;

    @PostMapping
    public Result<Void> save(@Valid @RequestBody ScheduleDTO scheduleDTO) {
        scheduleService.save(scheduleDTO);
        return Result.success();
    }

    @GetMapping
    public Result<List<TeacherScheduleDetailVO>> list() {
        return Result.success(scheduleService.listForCurrentTeacher());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ScheduleUpdateDTO updateDTO) {
        scheduleService.update(id, updateDTO);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        scheduleService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @GetMapping("/available")
    public Result<List<TeacherSchedule>> available(@RequestParam Long teacherId) {
        return Result.success(scheduleService.listAvailable(teacherId));
    }
}
