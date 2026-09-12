package com.rehab.controller.student;

import com.rehab.common.Result;
import com.rehab.pojo.dto.AppointmentDecisionDTO;
import com.rehab.pojo.dto.AppointmentSubmitDTO;
import com.rehab.pojo.vo.AppointmentDetailVO;
import com.rehab.pojo.vo.AppointmentSubmitVO;
import com.rehab.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/student/appointment")
public class StudentAppointmentController {
    @Autowired
    private AppointmentService appointmentService;

    @PostMapping
    public Result<AppointmentSubmitVO> submit(@Valid @RequestBody AppointmentSubmitDTO submitDTO) {
        return Result.success(appointmentService.submit(submitDTO));
    }

    @GetMapping("/status/{id}")
    public Result<String> status(@PathVariable Long id) {
        return Result.success(appointmentService.getSubmitStatus(id));
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody AppointmentDecisionDTO decisionDTO) {
        appointmentService.cancel(decisionDTO);
        return Result.success();
    }

    @GetMapping
    public Result<List<AppointmentDetailVO>> list() {
        return Result.success(appointmentService.listForStudent());
    }
}
