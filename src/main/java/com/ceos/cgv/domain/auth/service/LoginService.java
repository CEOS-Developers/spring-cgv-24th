package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.LoginRequest;
import com.ceos.cgv.domain.auth.dto.LoginResponse;
import com.ceos.cgv.global.security.CgvUserDetails;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.loginId(), request.password()));
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        CgvUserDetails user = (CgvUserDetails) authentication.getPrincipal();
        return refreshTokenService.issueForLogin(user.userId());
    }
}
