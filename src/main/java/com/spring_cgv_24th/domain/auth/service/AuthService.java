package com.spring_cgv_24th.domain.auth.service;

import com.spring_cgv_24th.domain.auth.dto.AuthReqDTO;
import com.spring_cgv_24th.domain.auth.dto.AuthResDTO;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResDTO.SignUpResDTO signUp(AuthReqDTO.SignUpReqDTO request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS);
        }

        Member member = Member.builder()
                .email(request.email())
                .name(request.name())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(MemberRole.USER)
                .build();

        return AuthResDTO.SignUpResDTO.from(memberRepository.save(member));
    }

    // 회원 조회와 Refresh Token 저장은 각 서비스의 트랜잭션으로 처리한다.
    public AuthResDTO.LoginResDTO login(AuthReqDTO.LoginReqDTO request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(), request.password()));
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtProvider.createAccessToken(
                    principal.getMemberId(), principal.getRole());
            String refreshToken = refreshTokenService.issue(principal.getMemberId());
            return AuthResDTO.LoginResDTO.from(accessToken, refreshToken);
        } catch (BadCredentialsException e) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
    }

    // 회원 행 잠금이 Access Token 발급까지 유지되도록 쓰기 트랜잭션을 사용한다.
    @Transactional
    public AuthResDTO.RefreshResDTO refresh(AuthReqDTO.RefreshReqDTO request) {
        Member member = refreshTokenService.findMemberByValidToken(request.refreshToken());
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        return AuthResDTO.RefreshResDTO.from(accessToken);
    }

    @Transactional
    public void logout(AuthReqDTO.LogoutReqDTO request) {
        refreshTokenService.revoke(request.refreshToken());
    }
}
