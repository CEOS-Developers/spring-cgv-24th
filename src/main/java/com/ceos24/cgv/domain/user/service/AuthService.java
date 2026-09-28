package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.request.SignUpRequest;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorCode;
import com.ceos24.cgv.domain.user.exception.UserException;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ceos24.cgv.domain.user.dto.request.LoginRequest;
import com.ceos24.cgv.domain.user.dto.response.LoginResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    //회원가입
    @Transactional
    public ApiResponse<Void> signup(SignUpRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new UserException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.builder()
                .nickname(request.nickname())
                .birthday(request.birthday())
                .loginId(request.loginId())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .build();

        userRepository.save(user);

        return ApiResponse.onSuccess("회원가입에 성공했습니다.");
    }


    // 로그인

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.loginId(),
                        request.password()
                )
        );

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.createAccessToken(
                userDetails.getUserId(),
                userDetails.getUserRole()
        );

        return new LoginResponse(accessToken);
    }
}
