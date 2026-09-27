package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.LoginRequest;
import com.ceos24.cgv.domain.user.dto.LoginResponse;
import com.ceos24.cgv.domain.user.dto.LogoutRequest;
import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.ceos24.cgv.domain.user.dto.SignupResponse;
import com.ceos24.cgv.domain.user.dto.TokenReissueRequest;
import com.ceos24.cgv.domain.user.dto.TokenReissueResponse;
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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    // 역할은 리프레시 토큰이 아니라 DB의 사용자에서 읽는다. 권한이 바뀌었다면 재발급 시점에 반영된다.
    public TokenReissueResponse reissue(TokenReissueRequest req) {
        RefreshToken refreshToken = refreshTokenRepository
                .findWithUserByTokenHash(refreshTokenProvider.hash(req.refreshToken()))
                .orElseThrow(() -> refreshTokenRejected("not_found", null));

        if (!refreshToken.isUsableAt(LocalDateTime.now(clock))) {
            throw refreshTokenRejected(refreshToken.isRevoked() ? "revoked" : "expired", refreshToken.getId());
        }

        User user = refreshToken.getUser();
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        return TokenReissueResponse.of(accessToken, jwtProvider.getAccessTokenValiditySeconds());
    }

    // 없는 토큰이어도 실패로 알리지 않는다. 클라이언트는 어차피 가진 토큰을 버리므로 알려도 할 일이 없다.
    // 이 토큰으로 발급된 액세스 토큰은 만료 전까지 계속 유효하다. 막으려면 액세스 토큰 차단 목록이 필요하다.
    @Transactional
    public void logout(LogoutRequest req) {
        refreshTokenRepository.findByTokenHash(refreshTokenProvider.hash(req.refreshToken()))
                .ifPresent(token -> token.revoke(LocalDateTime.now(clock)));
    }

    // 응답은 원인을 나누지 않지만 서버 로그에는 남긴다. 원문 토큰은 로그에 쓰지 않는다.
    private CustomException refreshTokenRejected(String reason, Long tokenId) {
        log.warn("[RefreshToken] 재발급 거부 reason={} tokenId={}", reason, tokenId);
        return new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
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
