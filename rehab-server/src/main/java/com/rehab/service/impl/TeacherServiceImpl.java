package com.rehab.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rehab.mapper.TeacherMapper;
import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.pojo.entity.Teacher;
import com.rehab.pojo.vo.TeacherVO;
import com.rehab.pojo.vo.TeacherAdminVO;
import com.rehab.service.TeacherService;
import com.rehab.service.AuthTokenService;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生端教师查询服务。
 * 对外返回 TeacherVO 而不是数据库实体，防止手机号、密码哈希和内部状态等敏感字段泄露。
 */
@Service
public class TeacherServiceImpl implements TeacherService {
    @Autowired
    private TeacherMapper teacherMapper;
    @Autowired
    private RedisCacheClient redisCacheClient;
    @Autowired
    private AuthTokenService authTokenService;

    @Override
    public List<TeacherVO> listEnabled() {
        List<TeacherVO> cached = redisCacheClient.queryList(
                RedisKeys.CACHE_TEACHER_LIST_KEY,
                new TypeReference<List<TeacherVO>>() {
                },
                () -> teacherMapper.listEnabled().stream()
                        .map(this::toVO)
                        .collect(Collectors.toList()));
        return cached;
    }

    @Override
    public List<TeacherAdminVO> listForAdmin() {
        return teacherMapper.listForAdmin();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BusinessException.badRequest("teacher status must be 0 or 1");
        }
        if (BaseContext.getCurrentId().equals(id) && status == 0) {
            throw BusinessException.conflict("current administrator cannot disable their own account");
        }
        if (teacherMapper.updateStatus(id, status) != 1) {
            throw BusinessException.badRequest("teacher does not exist");
        }
        if (status == 0) {
            authTokenService.revokeUserSessions(id, "teacher");
            authTokenService.revokeUserSessions(id, "admin");
            com.rehab.websocket.AppointmentWebSocket.closeTeacherSessions(id);
        }
        redisCacheClient.delete(RedisKeys.CACHE_TEACHER_LIST_KEY);
    }

    private TeacherVO toVO(Teacher teacher) {
        return new TeacherVO(teacher.getId(), teacher.getName(), teacher.getSpecialty(), teacher.getIntroduction());
    }
}
