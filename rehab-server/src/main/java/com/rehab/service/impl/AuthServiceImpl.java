package com.rehab.service.impl;

import com.rehab.config.BusinessException;
import com.rehab.mapper.StudentMapper;
import com.rehab.mapper.TeacherMapper;
import com.rehab.pojo.dto.LoginDTO;
import com.rehab.pojo.entity.Student;
import com.rehab.pojo.entity.Teacher;
import com.rehab.service.AuthResult;
import com.rehab.service.AuthService;
import com.rehab.service.AuthTokenService;
import com.rehab.utils.PasswordUtil;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录与教师账号创建服务。
 * 新密码统一使用 BCrypt；旧版 salted SHA-256 仅用于兼容历史数据，登录成功后立即升级。
 * 角色由服务端写入，不能信任客户端提交的 role 字段。
 */
@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private TeacherMapper teacherMapper;
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private RedisCacheClient redisCacheClient;
    @Autowired
    private AuthTokenService authTokenService;

    @Override
    public AuthResult teacherLogin(LoginDTO loginDTO) {
        Teacher teacher = teacherMapper.getByPhone(loginDTO.getPhone());
        if (teacher == null || !PasswordUtil.matches(loginDTO.getPassword(), teacher.getPassword())) {
            throw BusinessException.badRequest("teacher account or password is incorrect");
        }
        if (!Integer.valueOf(1).equals(teacher.getStatus())) {
            throw BusinessException.forbidden("teacher account is disabled");
        }
        if (PasswordUtil.needsUpgrade(teacher.getPassword())) {
            // 渐进式迁移避免上线时一次性重置全部历史账号密码。
            teacherMapper.updatePassword(teacher.getId(), PasswordUtil.encode(loginDTO.getPassword()));
            authTokenService.revokeUserSessions(teacher.getId(),
                    teacher.getRole() == null ? "teacher" : teacher.getRole());
        }
        String role = teacher.getRole() == null ? "teacher" : teacher.getRole();
        return authTokenService.issue(teacher.getId(), teacher.getName(), role);
    }

    @Override
    public AuthResult studentLogin(LoginDTO loginDTO) {
        // 根据手机号查询学生
        Student student = studentMapper.getByPhone(loginDTO.getPhone());
        // 手机号不存在 → 自动创建默认学生账号
        if (student == null) {
            student = studentMapper.createDefault(loginDTO.getPhone(), PasswordUtil.encode(loginDTO.getPassword()));
        }
        // 密码校验
        if (!PasswordUtil.matches(loginDTO.getPassword(), student.getPassword())) {
            throw BusinessException.badRequest("student account or password is incorrect");
        }
        // 校验账号状态：status=1 代表启用
        if (!Integer.valueOf(1).equals(student.getStatus())) {
            throw BusinessException.forbidden("student account is disabled");
        }
        // 判断密码哈希是否需要升级加密算法
        if (PasswordUtil.needsUpgrade(student.getPassword())) {
            studentMapper.updatePassword(student.getId(), PasswordUtil.encode(loginDTO.getPassword()));
            authTokenService.revokeUserSessions(student.getId(), "student");
        }
        // 颁发令牌，生成会话，返回AuthResult
        return authTokenService.issue(student.getId(), student.getName(), "student");
    }


    @Override
    public AuthResult refresh(String refreshToken) {
        return authTokenService.refresh(refreshToken);
    }

    @Override
    public void logout(String sessionId) {
        authTokenService.logout(sessionId);
    }

    @Override
    public void createTeacher(Teacher teacher) {
        if (teacherMapper.getByPhone(teacher.getPhone()) != null) {
            throw BusinessException.conflict("teacher phone already exists");
        }
        teacher.setPassword(PasswordUtil.encode(teacher.getPassword()));
        teacher.setStatus(1);
        teacher.setRole("teacher"); // 防止调用方通过请求体自行创建管理员账号。
        teacher.setCreateTime(LocalDateTime.now());
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.insert(teacher);
        redisCacheClient.delete(RedisKeys.CACHE_TEACHER_LIST_KEY);
    }
}
