package com.rehab.service;

import com.rehab.pojo.dto.LoginDTO;
import com.rehab.pojo.entity.Teacher;

public interface AuthService {
    AuthResult teacherLogin(LoginDTO loginDTO);

    AuthResult studentLogin(LoginDTO loginDTO);

    AuthResult refresh(String refreshToken);

    void logout(String sessionId);

    void createTeacher(Teacher teacher);
}
