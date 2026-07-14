package com.matessa.user.service;

import com.matessa.user.dto.request.LoginRequest;
import com.matessa.user.dto.request.RegisterRequest;
import com.matessa.user.dto.response.TokenResponse;
import com.matessa.user.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(String refreshTokenValue);

    void logout(String refreshTokenValue);
}
