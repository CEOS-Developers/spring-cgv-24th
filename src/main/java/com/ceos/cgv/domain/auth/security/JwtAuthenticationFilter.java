package com.ceos.cgv.domain.auth.security;

import com.ceos.cgv.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint entryPoint;
    private final PublicApiRequestMatcher publicApiMatcher;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RestAuthenticationEntryPoint entryPoint,
                                   PublicApiRequestMatcher publicApiMatcher) {
        this.jwtService = jwtService;
        this.entryPoint = entryPoint;
        this.publicApiMatcher = publicApiMatcher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicApiMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() == "Bearer ".length()) {
            reject(request, response, ErrorCode.TOKEN_INVALID);
            return;
        }
        try {
            JwtService.VerifiedToken token = jwtService.verify(header.substring("Bearer ".length()));
            AuthenticatedUser principal = new AuthenticatedUser(token.userId(), token.role());
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + token.role().name())));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (ExpiredJwtException exception) {
            reject(request, response, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException exception) {
            reject(request, response, ErrorCode.TOKEN_INVALID);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        ErrorCode errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        request.setAttribute(RestAuthenticationEntryPoint.ERROR_ATTRIBUTE, errorCode);
        entryPoint.commence(request, response, new BadCredentialsException(errorCode.name()));
    }
}
