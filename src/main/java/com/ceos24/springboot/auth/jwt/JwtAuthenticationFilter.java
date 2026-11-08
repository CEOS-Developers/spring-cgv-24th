package com.ceos24.springboot.auth.jwt;

import com.ceos24.springboot.user.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

        CustomUserDetails userDetails;
        // getUserId()에서 서명과 만료를 검증하도록 수정
        try {
            Long userId = jwtProvider.getUserId(token);
            userDetails = customUserDetailsService.loadUserById(userId);
        } catch (ExpiredJwtException e) {
            handleAuthenticationFailure(request, response, ErrorCode.TOKEN_EXPIRED, e);
            return;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            handleAuthenticationFailure(request, response, ErrorCode.TOKEN_INVALID, e);
            return;
        }


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

    private void handleAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            RuntimeException cause
    ) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        request.setAttribute("errorCode", errorCode);
        jwtAuthenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException(errorCode.getMessage(), cause)
        );
    }

    private String getAccessToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }
}
