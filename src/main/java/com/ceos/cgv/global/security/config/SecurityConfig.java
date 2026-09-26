package com.ceos.cgv.global.security.config;

import com.ceos.cgv.global.security.handler.RestAccessDeniedHandler;
import com.ceos.cgv.global.security.handler.RestAuthenticationEntryPoint;
import com.ceos.cgv.global.security.jwt.JwtAuthenticationFilter;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Set;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
        RequestMatcher reservations = request -> request.getRequestURI()
                .substring(request.getContextPath().length())
                .matches("/api/v1/reservations(?:/[^/]+)?")
                && ("POST".equals(request.getMethod()) || "GET".equals(request.getMethod())
                || "DELETE".equals(request.getMethod()));
        RequestMatcher foodOrders = request -> request.getRequestURI()
                .substring(request.getContextPath().length())
                .matches("/api/v1/food-orders(?:/[^/]+)?")
                && ("POST".equals(request.getMethod()) || "GET".equals(request.getMethod()));
        RequestMatcher seatHolds = request -> request.getRequestURI()
                .substring(request.getContextPath().length())
                .matches("/api/v1/seat-holds(?:/[^/]+(?:/confirm)?)?")
                && ("POST".equals(request.getMethod()) || "DELETE".equals(request.getMethod())
                || "GET".equals(request.getMethod()));
        Set<String> adminRegistrationPaths = Set.of(
                "/api/v1/screens", "/api/v1/screenings",
                "/api/v1/products", "/api/v1/inventories");
        RequestMatcher adminWrites = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return ("POST".equals(request.getMethod())
                    && (path.equals("/api/v1/movies") || adminRegistrationPaths.contains(path)))
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
                        .requestMatchers(reservations).authenticated()
                        .requestMatchers(foodOrders).authenticated()
                        .requestMatchers(seatHolds).authenticated()
                        .requestMatchers(adminWrites).hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, entryPoint, publicApiMatcher),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
