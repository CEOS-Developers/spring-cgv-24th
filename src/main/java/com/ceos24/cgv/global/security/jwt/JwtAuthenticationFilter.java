package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.exception.SecurityErrorCode;
import com.ceos24.cgv.global.security.handler.CustomAuthenticationEntryPoint;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.ceos24.cgv.global.security.handler.SecurityResponseWriter.AUTH_ERROR;

@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(
                        HttpHeaders.AUTHORIZATION
                );

        if (!StringUtils.hasText(authorizationHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token =
                    resolveToken(authorizationHeader);

            Authentication authentication =
                    getAuthentication(token);

            SecurityContext context =
                    SecurityContextHolder
                            .createEmptyContext();

            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

        } catch (ExpiredJwtException e) {

            request.setAttribute(
                    AUTH_ERROR,
                    SecurityErrorCode.TOKEN_EXPIRED
            );

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(
                            "Expired access token",
                            e
                    )
            );

            return;

        } catch (JwtException | IllegalArgumentException e) {

            request.setAttribute(
                    AUTH_ERROR,
                    SecurityErrorCode.TOKEN_INVALID
            );

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(
                            "Invalid access token",
                            e
                    )
            );

            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path = request.getRequestURI();

        return path.equals("/api/auth/login")
                || path.equals("/api/auth/signup")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/error");
    }

    private String resolveToken(
            String authorizationHeader
    ) {

        if (!authorizationHeader.startsWith(
                BEARER_PREFIX
        )) {
            throw new IllegalArgumentException(
                    "Authorization header must use Bearer scheme."
            );
        }

        String token = authorizationHeader.substring(
                BEARER_PREFIX.length()
        );

        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException(
                    "Bearer token is empty."
            );
        }

        return token;
    }

    private Authentication getAuthentication(
            String token
    ) {

        AccessTokenInfo accessTokenInfo =
                jwtTokenProvider.parseAccessToken(token);

        CustomUserDetails userDetails =
                new CustomUserDetails(
                        accessTokenInfo.userId(),
                        accessTokenInfo.role()
                );

        return UsernamePasswordAuthenticationToken
                .authenticated(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
    }
}
