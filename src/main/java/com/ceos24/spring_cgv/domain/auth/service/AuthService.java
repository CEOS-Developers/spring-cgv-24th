package com.ceos24.spring_cgv.domain.auth.service;

import com.ceos24.spring_cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.spring_cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.spring_cgv.domain.auth.dto.response.LoginResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.ReissueResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.SignUpResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.TokenIssueResult;
import com.ceos24.spring_cgv.domain.auth.enums.RefreshTokenStatus;
import com.ceos24.spring_cgv.domain.auth.enums.TokenType;
import com.ceos24.spring_cgv.domain.auth.exception.AuthException;
import com.ceos24.spring_cgv.domain.auth.exception.code.AuthErrorCode;
import com.ceos24.spring_cgv.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.spring_cgv.domain.auth.repository.TokenBlacklistRepository;
import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.member.enums.Role;
import com.ceos24.spring_cgv.domain.member.exception.MemberException;
import com.ceos24.spring_cgv.domain.member.exception.code.MemberErrorCode;
import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import com.ceos24.spring_cgv.global.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
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
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    /***
     * 함수 기능: 회원가입
     * @param request
     * @return
     */
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
        log.info("[회원가입] memberId={}", savedMember.getId());

        return new SignUpResponse(savedMember.getCreatedAt());
    }

    /***
     * 함수 기능: 로그인을 수행한다. 성공 시, AT(Response Body)와 RT(Cookie)가 반환된다.
     * @param request 로그인 요청 (이메일, pw)
     * @return TokenIssueResult AtInfo, MemberInfo, RT
     */
    public TokenIssueResult<LoginResponse> login(LoginRequest request) {

        try {
            UsernamePasswordAuthenticationToken unauthenticatedToken = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());

            Authentication authentication = authenticationManager.authenticate(unauthenticatedToken);
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

            // 인증 후, JwtUtil을 통해 AT, RT 발급
            String at = jwtUtil.createAT(principal.getMemberId(), principal.getRole());
            String rt = jwtUtil.createRT(principal.getMemberId());

            // 발급된 RT는 Redis에 저장
            refreshTokenRepository.save(principal.getMemberId(), rt, Duration.ofSeconds(jwtUtil.getRtValiditySeconds()));

            // AT는 응답 형식에 맞춰 반환, RT는 컨트롤러에서 쿠키에 담아 응답함.
            LoginResponse.AtInfo atInfo = LoginResponse.AtInfo.of(at, TokenType.AT, jwtUtil.getAtValiditySeconds());

            // 인증객체를 통해 회원 조회
            Member member = memberRepository.findById(principal.getMemberId())
                    .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
            LoginResponse.MemberInfo memberInfo = LoginResponse.MemberInfo.from(member);

            log.info("[로그인] memberId={}", member.getId());

            return new TokenIssueResult<>(LoginResponse.of(atInfo, memberInfo), rt);
        } catch (BadCredentialsException e){
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (AuthenticationException e){
            log.warn("[로그인 실패] {}", e.getMessage());
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
    }

    /***
     * 함수 기능: RT를 검증하고 AT와 RT를 재발급한다. 사용된 RT는 폐기된다. (RTR)
     * @param refreshToken 쿠키로 전달된 RT 원문
     * @return 새 AT를 담은 응답 바디와 쿠키로 내려보낼 새 RT
     */
    public TokenIssueResult<ReissueResponse> reissue(String refreshToken) {

        // refreshToken 쿠키가 오지 않은 경우
        if (!StringUtils.hasText(refreshToken)){
            throw new AuthException(AuthErrorCode.RT_COOKIE_MISSING);
        }

        // 제시된 RT 검증
        Claims claims = jwtUtil.parseRT(refreshToken);
        Long memberId = Long.parseLong(claims.getSubject());

        // Redis에 담긴 RT와 제시된 RT를 비교 후 분기
        // NOT_FOUND = 로그아웃 혹은 무효화된 세션 -> 재로그인 유도
        // MISMATCH = 재사용 감지 -> RT 폐기 후 재로그인 유도
        // VALID = 정상 로직
        RefreshTokenStatus status = refreshTokenRepository.verify(memberId, refreshToken);

        if (status == RefreshTokenStatus.NOT_FOUND){
            throw new AuthException(AuthErrorCode.RT_NOT_FOUND);
        } else if (status == RefreshTokenStatus.MISMATCH){
            log.warn("[RT 재사용 감지] memberId={}", memberId);
            refreshTokenRepository.delete(memberId);
            throw new AuthException(AuthErrorCode.RT_REUSE_DETECTED);
        }

        // 새 토큰 발급 후, RT는 Redis에 저장
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        String newAt = jwtUtil.createAT(memberId, member.getRole());
        String newRt = jwtUtil.createRT(memberId);

        refreshTokenRepository.save(memberId, newRt, Duration.ofSeconds(jwtUtil.getRtValiditySeconds()));

        log.info("[토큰 재발급] memberId={}", memberId);

        // 응답 생성
        LoginResponse.AtInfo atInfo = LoginResponse.AtInfo.of(newAt, TokenType.AT, jwtUtil.getAtValiditySeconds());
        return new TokenIssueResult<>(new ReissueResponse(atInfo), newRt);
    }

    /***
     * 함수 기능: 로그아웃을 수행한다. RT를 폐기해 재발급을 막고, 현재 AT를 bl에 등록해 무효화한다.
     * @param memberId 회원 식별자
     * @param bearerToken Authorization 헤더 원문 ("Bearer {AT}")
     */
    public void logout(Long memberId, String bearerToken){

        // RT를 제거하여 재발급 경로를 먼저 끊는다.
        refreshTokenRepository.delete(memberId);

        // AT를 파싱한다.
        Claims claims = jwtUtil.parseAT(bearerToken.substring(7));

        // AT를 블랙리스트에 추가한다.
        tokenBlacklistRepository.blacklist(
                claims.getId(),
                Duration.ofMillis(jwtUtil.getRemainingMillis(claims)),
                "logout");

        log.info("[로그아웃] memberId={}", memberId);
    }
}
