package com.ceos24.spring_cgv.domain.auth.service;

import com.ceos24.spring_cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.spring_cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.spring_cgv.domain.auth.dto.response.LoginResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.SignUpResponse;
import com.ceos24.spring_cgv.domain.auth.exception.AuthException;
import com.ceos24.spring_cgv.domain.auth.exception.code.AuthErrorCode;
import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.member.enums.Role;
import com.ceos24.spring_cgv.domain.member.exception.MemberException;
import com.ceos24.spring_cgv.domain.member.exception.code.MemberErrorCode;
import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import com.ceos24.spring_cgv.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {

        // 중복 이메일 여부 확인
        if (memberRepository.existsByEmail(request.email())){
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 비밀번호 암호화하여 회원 객체 저장
        Member savedMember = memberRepository.save(
                Member.builder()
                        .name(request.name())
                        .email(request.email())
                        .password(passwordEncoder.encode(request.password()))
                        .role(Role.USER)
                        .build()
        );

        return new SignUpResponse(savedMember.getCreatedAt());
    }

    public LoginResponse login(LoginRequest request) {

        try {
            UsernamePasswordAuthenticationToken unauthenticatedToken = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());

            Authentication authentication = authenticationManager.authenticate(unauthenticatedToken);
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

            // 인증 후, JwtUtil을 통해 AT, RT 발급
            String AT = jwtUtil.createAT(principal.getMemberId(), principal.getRole());
            String RT = jwtUtil.createRT(principal.getMemberId());

            LoginResponse.AtInfo atInfo = LoginResponse.AtInfo.of(AT, "AT", jwtUtil.getAtValiditySeconds());
            LoginResponse.RtInfo rtInfo = LoginResponse.RtInfo.of(RT, "RT", jwtUtil.getRtValiditySeconds());

            // 인증객체를 통해 회원 조회
            Member member = memberRepository.findById(principal.getMemberId())
                    .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
            LoginResponse.MemberInfo memberInfo = LoginResponse.MemberInfo.from(member);

            return LoginResponse.of(atInfo, rtInfo, memberInfo);
        } catch (BadCredentialsException e){
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (AuthenticationException e){
            log.warn("[로그인 실패] {}", e.getMessage());
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
    }
}
