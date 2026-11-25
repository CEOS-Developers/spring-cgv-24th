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
import io.jsonwebtoken.JwtException;
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
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            @Value("${admin.token}") String adminToken,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AuthenticationManager authenticationManager,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenService refreshTokenService) {
        this.adminToken = adminToken;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
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

            refreshTokenService.replace(userDetails.getMemberId(), refreshToken);

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
                    log.info("reissue_grace_period memberId={} note=return_previous_tokens", memberIdStr);
                    return new TokenResponse(
                            storedToken.getReissuedAccessToken(), storedToken.getReissuedRefreshToken());
                }

                refreshTokenRepository.deleteAllByMemberId(Long.parseLong(memberIdStr));
                log.error("refresh_token_reuse_detected memberId={} action=revoked_all_tokens", memberIdStr);
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
        } catch (JwtException | IllegalArgumentException e) {
            // 이어서 던지는 BusinessException을 GlobalExceptionHandler가 warn으로 남기므로 여기서는 debug.
            // 예외 메시지에 토큰 내용이 섞일 수 있어 예외 종류만 기록한다
            log.debug("reissue_rejected type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    @Transactional
    public void logout(Long memberId, String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        if (!storedToken.getMemberId().equals(memberId)) {
            log.warn(
                    "logout_rejected reason=token_owner_mismatch requestMemberId={} tokenMemberId={}",
                    memberId,
                    storedToken.getMemberId());
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 재발급으로 사용 처리된 이전 토큰이 누적되지 않도록 해당 회원의 토큰을 모두 정리한다.
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }
}
