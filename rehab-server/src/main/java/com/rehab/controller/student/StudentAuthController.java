package com.rehab.controller.student;

import com.rehab.common.Result;
import com.rehab.common.BaseContext;
import com.rehab.pojo.dto.LoginDTO;
import com.rehab.pojo.vo.LoginVO;
import com.rehab.service.AuthService;
import com.rehab.service.AuthCookieService;
import com.rehab.service.AuthResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/student")
public class StudentAuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private AuthCookieService authCookieService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        return respond(authService.studentLogin(loginDTO), response);
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

    /*
    *
    * > 登录（或者刷新令牌）流程的**收尾封装方法**：
        1. 把 `refreshToken` 通过 HttpOnly Cookie 写入 HTTP 响应返回浏览器；
        2. 把 `accessToken` 等登录信息放到 JSON 响应体返回给前端。
    * */
    private Result<LoginVO> respond(AuthResult result, HttpServletResponse response) {
        authCookieService.writeRefreshCookie(response, result.getRefreshToken(), result.getRefreshTokenExpiresIn());
        return Result.success(result.getLogin());
    }
}
