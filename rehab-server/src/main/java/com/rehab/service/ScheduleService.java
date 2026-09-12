package com.rehab.service;

import com.rehab.pojo.dto.ScheduleDTO;
import com.rehab.pojo.dto.ScheduleUpdateDTO;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.TeacherScheduleDetailVO;

import java.util.List;

public interface ScheduleService {
    void save(ScheduleDTO scheduleDTO);

    List<TeacherSchedule> listAvailable(Long teacherId);

    List<TeacherScheduleDetailVO> listForCurrentTeacher();

    void update(Long id, ScheduleUpdateDTO updateDTO);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
