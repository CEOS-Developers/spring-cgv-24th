package com.ceos24.spring_cgv.global.security.filter;

import com.ceos24.spring_cgv.domain.auth.exception.code.AuthErrorCode;
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
            } catch (Exception e){
                log.error("토큰 검증 중 오류가 발생했습니다.", e);
                request.setAttribute("exception", AuthErrorCode.UNKNOWN_TOKEN_ERROR);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Authentication getAuthentication(String token) {

        Claims claims = jwtUtil.parseAT(token);

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
