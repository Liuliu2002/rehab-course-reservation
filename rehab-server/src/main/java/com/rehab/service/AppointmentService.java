package com.rehab.service;

import com.rehab.pojo.dto.AppointmentDecisionDTO;
import com.rehab.pojo.dto.AppointmentSubmitDTO;
import com.rehab.pojo.vo.AppointmentDetailVO;
import com.rehab.pojo.vo.AppointmentSubmitVO;

import java.util.List;

public interface AppointmentService {
    AppointmentSubmitVO submit(AppointmentSubmitDTO submitDTO);

    String getSubmitStatus(Long appointmentId);

    void confirm(Long appointmentId);

    void reject(AppointmentDecisionDTO decisionDTO);

    void cancel(AppointmentDecisionDTO decisionDTO);

    void complete(Long appointmentId);

    List<AppointmentDetailVO> listForTeacher();

    List<AppointmentDetailVO> listForStudent();
}
