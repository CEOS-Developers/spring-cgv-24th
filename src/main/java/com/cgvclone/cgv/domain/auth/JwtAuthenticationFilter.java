package com.cgvclone.cgv.domain.auth;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern BEARER_PATTERN = Pattern.compile("^Bearer +([^\\s,]+)$", Pattern.CASE_INSENSITIVE);

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
            headers.nextElement();
            Matcher matcher = BEARER_PATTERN.matcher(authorization);
            if (headers.hasMoreElements() || !matcher.matches()) {
                throw new GlobalException(ErrorCode.TOKEN_INVALID);
            }
            VerifiedAccessToken token = jwtTokenProvider.verifyAccessToken(matcher.group(1));
            CustomUserDetails userDetails = new CustomUserDetails(token);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (GlobalException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.writeError(response, exception.getErrorCode());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
