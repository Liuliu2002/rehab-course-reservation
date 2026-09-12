package com.rehab.service;

import com.rehab.pojo.dto.CourseDTO;
import com.rehab.pojo.entity.RehabCourse;

import java.util.List;

public interface CourseService {
    void save(CourseDTO courseDTO);

    List<RehabCourse> listAll();

    List<RehabCourse> listEnabled();

    RehabCourse detail(Long id);

    void updateStatus(Long id, Integer status);
}
