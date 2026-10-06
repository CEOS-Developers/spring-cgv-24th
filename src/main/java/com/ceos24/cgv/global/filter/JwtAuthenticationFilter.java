package com.ceos24.cgv.global.filter;

import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.apiPayload.response.ErrorResponse;
import com.ceos24.cgv.global.exception.JwtTokenExpiredException;
import com.ceos24.cgv.global.exception.JwtTokenInvalidException;
import com.ceos24.cgv.global.jwt.JwtTokenClaims;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String accessToken = resolveAccessToken(request);

            if (accessToken == null) {
                filterChain.doFilter(request, response);
                return;
            }

            JwtTokenClaims claims = jwtTokenProvider.parseAccessToken(accessToken);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                setAuthentication(claims);
            }

            filterChain.doFilter(request, response);
        } catch (JwtTokenExpiredException exception) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtTokenInvalidException exception) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.TOKEN_INVALID);
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorization)) {
            return null;
        }

        if (authorization.equalsIgnoreCase(BEARER_PREFIX.trim())) {
            throw new JwtTokenInvalidException();
        }

        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        if (!StringUtils.hasText(token)) {
            throw new JwtTokenInvalidException();
        }

        return token;
    }

    private void setAuthentication(JwtTokenClaims claims) {
        UserDetails principal = User.withUsername(claims.username())
                .password("")
                .authorities(claims.role())
                .build();

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getWriter(),
                ErrorResponse.of(errorCode)
        );
    }
}
