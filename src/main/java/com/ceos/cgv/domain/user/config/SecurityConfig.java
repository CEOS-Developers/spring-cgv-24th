package com.ceos.cgv.domain.user.config;

import com.ceos.cgv.domain.user.security.JwtAuthenticationFilter;
import com.ceos.cgv.domain.user.security.JwtService;
import com.ceos.cgv.domain.user.security.PublicApiRequestMatcher;
import com.ceos.cgv.domain.user.security.RestAccessDeniedHandler;
import com.ceos.cgv.domain.user.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        PublicApiRequestMatcher publicApiMatcher = new PublicApiRequestMatcher();
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
        RequestMatcher movieLike = request -> "POST".equals(request.getMethod())
                && request.getRequestURI().substring(request.getContextPath().length())
                .matches("/api/v1/movies/[^/]+/likes");
        RequestMatcher cinemaLike = request -> "POST".equals(request.getMethod())
                && request.getRequestURI().substring(request.getContextPath().length())
                .matches("/api/v1/cinemas/[^/]+/likes");
        RequestMatcher adminMovie = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return ("POST".equals(request.getMethod()) && path.equals("/api/v1/movies"))
                    || ("DELETE".equals(request.getMethod())
                    && path.matches("/api/v1/movies/[^/]+"));
        };

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicApiMatcher).permitAll()
                        .requestMatchers(movieLike).authenticated()
                        .requestMatchers(cinemaLike).authenticated()
                        .requestMatchers(adminMovie).hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, entryPoint, publicApiMatcher),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
