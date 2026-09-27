package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.LoginRequest;
import com.ceos24.cgv.domain.user.dto.LoginResponse;
import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.ceos24.cgv.domain.user.dto.SignupResponse;
import com.ceos24.cgv.domain.user.entity.RefreshToken;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.RefreshTokenRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.LoginUserDetails;
import com.ceos24.cgv.global.security.RefreshTokenProvider;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final Clock clock;

    @Transactional
    public SignupResponse signup(SignupRequest req) {
        if (userRepository.existsByLoginId(req.loginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.builder()
                .loginId(req.loginId())
                .password(passwordEncoder.encode(req.password()))
                .name(req.name())
                .birthDate(req.birthDate())
                .email(req.email())
                .phoneNumber(req.phoneNumber())
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // pre-check와 INSERT 사이에 같은 아이디가 먼저 가입한 경우다.
            // users의 unique 제약은 login_id 하나뿐이라 다른 원인으로 오역될 여지가 없다.
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        return SignupResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.loginId(), req.password()));
        } catch (BadCredentialsException e) {
            // 계정 없음도 여기로 온다(DaoAuthenticationProvider가 변환). 다른 AuthenticationException은
            // 잡지 않는다. DB 장애 같은 InternalAuthenticationServiceException이 로그인 실패로 가려지면 안 된다.
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        LoginUserDetails principal = (LoginUserDetails) authentication.getPrincipal();
        String accessToken = jwtProvider.createAccessToken(principal.getUserId(), principal.getRole());
        String refreshToken = issueRefreshToken(principal.getUserId());
        return LoginResponse.of(accessToken, jwtProvider.getAccessTokenValiditySeconds(),
                refreshToken, refreshTokenProvider.getValiditySeconds());
    }

    // 원문은 응답으로 한 번만 내보내고 DB에는 해시만 남긴다. 로그인마다 새 행이라 기기별 토큰이 따로 산다.
    private String issueRefreshToken(Long userId) {
        String rawToken = refreshTokenProvider.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(refreshTokenProvider.hash(rawToken))
                .expiresAt(refreshTokenProvider.expiresAt(LocalDateTime.now(clock)))
                .build());
        return rawToken;
    }
}
