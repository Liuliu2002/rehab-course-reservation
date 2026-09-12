package com.rehab.service;

import com.rehab.pojo.vo.TeacherVO;
import com.rehab.pojo.vo.TeacherAdminVO;

import java.util.List;

public interface TeacherService {
    List<TeacherVO> listEnabled();

    List<TeacherAdminVO> listForAdmin();

    void updateStatus(Long id, Integer status);
}
