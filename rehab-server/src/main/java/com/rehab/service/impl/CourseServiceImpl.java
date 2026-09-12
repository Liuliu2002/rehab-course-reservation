package com.rehab.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rehab.config.BusinessException;
import com.rehab.mapper.CourseMapper;
import com.rehab.pojo.dto.CourseDTO;
import com.rehab.pojo.entity.RehabCourse;
import com.rehab.service.CourseService;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程服务。课程属于读多写少数据，因此列表和详情使用 Cache Aside；
 * 管理端修改状态后同时删除列表缓存和详情缓存，下一次读取再从数据库回填。
 */
@Service
public class CourseServiceImpl implements CourseService {
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private RedisCacheClient redisCacheClient;

    @Override
    public void save(CourseDTO courseDTO) {
        RehabCourse course = new RehabCourse();
        BeanUtils.copyProperties(courseDTO, course);
        course.setStatus(1);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        redisCacheClient.delete(RedisKeys.CACHE_COURSE_LIST_KEY);
    }

    @Override
    public List<RehabCourse> listAll() {
        return courseMapper.listAll();
    }

    @Override
    public List<RehabCourse> listEnabled() {
        return redisCacheClient.queryList(
                RedisKeys.CACHE_COURSE_LIST_KEY,
                new TypeReference<List<RehabCourse>>() {
                },
                courseMapper::listEnabled);
    }

    @Override
    public RehabCourse detail(Long id) {
        RehabCourse course = redisCacheClient.queryObject(
                RedisKeys.CACHE_COURSE_KEY + id, RehabCourse.class, () -> courseMapper.getById(id));
        if (course == null || !Integer.valueOf(1).equals(course.getStatus())) {
            throw BusinessException.badRequest("course does not exist or is disabled");
        }
        return course;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BusinessException.badRequest("course status must be 0 or 1");
        }
        courseMapper.updateStatus(id, status);
        redisCacheClient.delete(RedisKeys.CACHE_COURSE_KEY + id);
        redisCacheClient.delete(RedisKeys.CACHE_COURSE_LIST_KEY);
    }
}
