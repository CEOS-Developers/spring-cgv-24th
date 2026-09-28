package com.cgvclone.cgv.domain.auth;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.auth.dto.LoginRequest;
import com.cgvclone.cgv.domain.auth.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    public LoginResponse login(LoginRequest request) {
        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new GlobalException(ErrorCode.LOGIN_FAILED);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(), request.password()));
        } catch (BadCredentialsException exception) {
            throw new GlobalException(ErrorCode.LOGIN_FAILED);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        // TODO 3번 미션에서 인증된 사용자 정보를 이용한 Access Token 발급을 연결
        return new LoginResponse(userDetails.getUserId(), userDetails.getUsername());
    }
}
