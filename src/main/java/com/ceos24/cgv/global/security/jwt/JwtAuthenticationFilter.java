package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.enums.UserRole;
import com.ceos24.cgv.global.security.CustomAuthenticationEntryPoint;
import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.exception.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        // 토큰이 없으면 인증 객체를 만들지 않고 다음 필터로
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더를 보냈지만 Bearer 형식이 아닌 경우
        if (!authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            entryPoint.commence(response, AuthErrorCode.TOKEN_INVALID);
            return;
        }

        try {
            String token = authorization.substring(7).trim();
            Claims claims = jwtTokenProvider.parseAndValidate(token);

            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get("role", String.class));

            CustomUserDetails principal =
                    new CustomUserDetails(userId,role);

            var authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(response, AuthErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(response, AuthErrorCode.TOKEN_INVALID);
            return;
        }

        filterChain.doFilter(request, response);
    }
}