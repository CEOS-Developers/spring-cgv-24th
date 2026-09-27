package com.ceos24.springboot.auth.jwt;

import com.ceos24.springboot.user.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;
import com.ceos24.springboot.user.security.CustomUserDetailsService;

import com.ceos24.springboot.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
// HTTP 요청이 들어올 때 JWT를 확인해야하므로 OncePerRequestFilter 를 상속받음

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        String token = getAccessToken(authorizationHeader);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // JWT 검증 로직
        try {
            jwtProvider.validateToken(token);
        } catch (ExpiredJwtException e) {

            request.setAttribute("errorCode", ErrorCode.TOKEN_EXPIRED);

            jwtAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException(
                            "토큰이 만료되었습니다.",
                            e
                    )
            );
            return;

        } catch (JwtException | IllegalArgumentException e) {

            request.setAttribute("errorCode", ErrorCode.TOKEN_INVALID);

            jwtAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException(
                            "유효하지 않은 토큰입니다.",
                            e
                    )
            );
            return;
        }

        // JWT에서 userId 추출
        Long userId = jwtProvider.getUserId(token);

        // userId로 사용자 조회
        CustomUserDetails userDetails = customUserDetailsService.loadUserById(userId);


        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,                  // principal
                        null,                         // credentials
                        userDetails.getAuthorities()  // authorities
                );

        // 인증된 사용자가 보낸 요청임을 판단
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private String getAccessToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }
}