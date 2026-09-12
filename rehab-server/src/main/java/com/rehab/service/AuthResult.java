package com.rehab.service;

import com.rehab.pojo.vo.LoginVO;

public class AuthResult {
    private final LoginVO login;
    private final String refreshToken;
    private final long refreshTokenExpiresIn;

    public AuthResult(LoginVO login, String refreshToken, long refreshTokenExpiresIn) {
        this.login = login;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }

    public LoginVO getLogin() {
        return login;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }
}
