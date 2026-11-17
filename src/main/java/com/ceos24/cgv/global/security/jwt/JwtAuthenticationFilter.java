package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.logging.MdcLoggingFilter;
import com.ceos24.cgv.global.security.ApiUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String token = resolveToken(request);
        try {
            if (token != null) {
                ApiUserDetails userDetails = jwtProvider.getAuthenticationInfo(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                MDC.put(MdcLoggingFilter.MEMBER_ID, String.valueOf(userDetails.memberId()));
            }
        } catch (ExpiredJwtException e) {
            // 최종 실패 로그는 GlobalExceptionHandler의 business_exception 한 줄로 남기므로 여기서는 debug
            log.debug("jwt_rejected reason=expired");
            request.setAttribute("exception", ErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (JwtException e) {
            // 예외 메시지에 토큰 내용이 섞일 수 있어 예외 종류만 남긴다
            log.debug("jwt_rejected reason=invalid type={}", e.getClass().getSimpleName());
            request.setAttribute("exception", ErrorCode.AUTH_INVALID_TOKEN);
        } catch (IllegalArgumentException e) {
            log.debug("jwt_rejected reason=missing");
            request.setAttribute("exception", ErrorCode.AUTH_TOKEN_NOT_EXIST);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
