package com.ceos24.spring_cgv.global.security.filter;

import com.ceos24.spring_cgv.domain.auth.exception.AuthException;
import com.ceos24.spring_cgv.domain.auth.exception.code.AuthErrorCode;
import com.ceos24.spring_cgv.domain.auth.repository.TokenBlacklistRepository;
import com.ceos24.spring_cgv.domain.member.enums.Role;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import com.ceos24.spring_cgv.global.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 요청 헤더에서 토큰 추출
        String token = resolveToken(request);

        if (StringUtils.hasText(token)){

            try {
                Authentication authentication = getAuthentication(token);

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (ProjectException e){
                request.setAttribute("exception", e.getErrorCode());
            } catch (DataAccessException e) {
                request.setAttribute("exception", AuthErrorCode.AUTH_STORE_UNAVAILABLE);
            } catch (RuntimeException e){
                log.error("토큰 검증 중 오류가 발생했습니다.", e);
                request.setAttribute("exception", AuthErrorCode.UNKNOWN_TOKEN_ERROR);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 파싱한 토큰을 검증하고 payload를 추출하여 인증객체를 생성한다.
    private Authentication getAuthentication(String token) {

        Claims claims = jwtUtil.parseAT(token);

        // 블랙리스트에 등록된 AT의 경우, 서명이 유효해도 거부한다.
        if (tokenBlacklistRepository.isBlacklisted(claims.getId())){
            log.warn("[블랙리스트 토큰 사용] jti={}", claims.getId());
            throw new AuthException(AuthErrorCode.BLACKLISTED_TOKEN);
        }

        CustomUserDetails userDetails = new CustomUserDetails(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get("role", String.class)),
                null);

        return UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());
    }

    // 요청 헤더에서 토큰을 추출한다.
    private String resolveToken(HttpServletRequest request) {

        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }

        return null;
    }
}
