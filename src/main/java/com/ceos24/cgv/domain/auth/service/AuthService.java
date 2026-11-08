package com.ceos24.cgv.domain.auth.service;

import com.ceos24.cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.cgv.domain.auth.dto.response.TokenResponse;
import com.ceos24.cgv.domain.auth.entity.RefreshToken;
import com.ceos24.cgv.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.cgv.domain.member.Role;
import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

    private final String adminToken;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
            @Value("${admin.token}") String adminToken,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AuthenticationManager authenticationManager,
            RefreshTokenRepository refreshTokenRepository) {
        this.adminToken = adminToken;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void signUp(SignUpRequest request) {

        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_ID_ALREADY_EXISTS);
        }

        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }

        Role role;

        if (adminToken.equals(request.adminToken())) {
            role = Role.ADMIN;
        } else {
            role = Role.USER;
        }

        Member member = Member.builder()
                .loginId(request.loginId())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(role)
                .build();
        memberRepository.save(member);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(request.loginId(), request.password());

            Authentication authentication = authenticationManager.authenticate(authenticationToken);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            String accessToken =
                    jwtProvider.createAccessToken(userDetails.getMemberId().toString(), userDetails.getRole());
            String refreshToken =
                    jwtProvider.createRefreshToken(userDetails.getMemberId().toString());

            refreshTokenRepository.deleteByMemberId(userDetails.getMemberId());
            refreshTokenRepository.save(new RefreshToken(refreshToken, userDetails.getMemberId()));

            return new TokenResponse(accessToken, refreshToken);
        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse reissue(String refreshToken) {
        try {
            String memberIdStr = jwtProvider.validateRefreshToken(refreshToken);

            RefreshToken storedToken = refreshTokenRepository
                    .findByToken(refreshToken)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

            if (storedToken.isUsed()) {
                if (storedToken.isWithinGracePeriod(GRACE_PERIOD)) {
                    log.info("[유예 기간 내 재발급] memberId: {} 동시 요청으로 직전 발급 토큰 반환", memberIdStr);
                    return new TokenResponse(
                            storedToken.getReissuedAccessToken(), storedToken.getReissuedRefreshToken());
                }

                refreshTokenRepository.deleteAllByMemberId(Long.parseLong(memberIdStr));
                log.error("[재사용 탐지] memberId: {} 의 토큰 탈취 의심 (유예 기간 만료)", memberIdStr);
                throw new BusinessException(ErrorCode.AUTH_HIJACK_DETECTED);
            }

            Member member = memberRepository
                    .findById(Long.parseLong(memberIdStr))
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            String newAccessToken =
                    jwtProvider.createAccessToken(memberIdStr, member.getRole().getKey());
            String newRefreshToken = jwtProvider.createRefreshToken(memberIdStr);

            storedToken.useToken(newAccessToken, newRefreshToken);
            refreshTokenRepository.save(new RefreshToken(newRefreshToken, Long.parseLong(memberIdStr)));

            return new TokenResponse(newAccessToken, newRefreshToken);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }
}
