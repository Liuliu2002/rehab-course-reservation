package com.rehab.controller.admin;

import com.rehab.common.Result;
import com.rehab.common.BaseContext;
import com.rehab.pojo.dto.LoginDTO;
import com.rehab.pojo.entity.Teacher;
import com.rehab.pojo.vo.LoginVO;
import com.rehab.pojo.vo.TeacherAdminVO;
import com.rehab.service.AuthService;
import com.rehab.service.AuthCookieService;
import com.rehab.service.AuthResult;
import com.rehab.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminAuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private AuthCookieService authCookieService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        return respond(authService.teacherLogin(loginDTO), response);
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(
            @org.springframework.web.bind.annotation.CookieValue(value = AuthCookieService.REFRESH_COOKIE_NAME,
                    required = false) String refreshToken,
            HttpServletResponse response) {
        return respond(authService.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        com.rehab.websocket.AppointmentWebSocket.closeAuthSession(BaseContext.getCurrentSessionId());
        authService.logout(BaseContext.getCurrentSessionId());
        authCookieService.clearRefreshCookie(response);
        return Result.success();
    }

    @PostMapping("/teacher")
    public Result<Void> createTeacher(@Valid @RequestBody Teacher teacher) {
        authService.createTeacher(teacher);
        return Result.success();
    }

    @GetMapping("/teacher")
    public Result<List<TeacherAdminVO>> listTeachers() {
        return Result.success(teacherService.listForAdmin());
    }

    @PutMapping("/teacher/{id}/status")
    public Result<Void> updateTeacherStatus(@PathVariable Long id, @RequestParam Integer status) {
        teacherService.updateStatus(id, status);
        return Result.success();
    }

    private Result<LoginVO> respond(AuthResult result, HttpServletResponse response) {
        authCookieService.writeRefreshCookie(response, result.getRefreshToken(), result.getRefreshTokenExpiresIn());
        return Result.success(result.getLogin());
    }
}
