package com.ceos24.springboot.auth.service;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.auth.exception.LoginFailedException;
import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.user.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest request) {

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.email(),
                                    request.password()
                            )
                    );

            CustomUserDetails userDetails =
                    (CustomUserDetails) authentication.getPrincipal();

            String accessToken =
                    jwtProvider.createAccessToken(
                            userDetails.getUserId()
                    );

            return LoginResponse.of(accessToken);

        } catch (AuthenticationException e) {
            throw new LoginFailedException();
        }
    }
}