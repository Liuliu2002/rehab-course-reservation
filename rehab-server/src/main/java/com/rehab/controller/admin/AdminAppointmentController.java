package com.rehab.controller.admin;

import com.rehab.common.Result;
import com.rehab.pojo.dto.AppointmentDecisionDTO;
import com.rehab.pojo.vo.AppointmentDetailVO;
import com.rehab.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/admin/appointment")
public class AdminAppointmentController {
    @Autowired
    private AppointmentService appointmentService;

    @GetMapping
    public Result<List<AppointmentDetailVO>> list() {
        return Result.success(appointmentService.listForTeacher());
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        appointmentService.confirm(id);
        return Result.success();
    }

    @PostMapping("/reject")
    public Result<Void> reject(@Valid @RequestBody AppointmentDecisionDTO decisionDTO) {
        appointmentService.reject(decisionDTO);
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        appointmentService.complete(id);
        return Result.success();
    }
}
