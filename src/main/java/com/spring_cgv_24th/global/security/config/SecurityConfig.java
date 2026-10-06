package com.spring_cgv_24th.global.security.config;

import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.security.filter.JwtAuthenticationFilter;
import com.spring_cgv_24th.global.security.handler.CustomAccessDeniedHandler;
import com.spring_cgv_24th.global.security.handler.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그아웃은 AuthController에서 Refresh Token 폐기로 처리한다.
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error")
                            .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout")
                            .permitAll()
                        // 새 관리자 API도 HTTP 메서드와 무관하게 ADMIN 권한을 요구한다.
                        .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/movies",
                                "/api/theaters",
                                "/api/theaters/*/auditoriums",
                                "/api/screenings")
                            .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/theaters/*/store/products/*/stock")
                            .hasRole("ADMIN")
                        // 공개 조회만 명시한다. 숫자 ID로 제한해 favorites 같은 보호 경로와 겹치지 않는다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/movies",
                                "/api/movies/{movieId:[0-9]+}",
                                "/api/theaters",
                                "/api/theaters/{theaterId:[0-9]+}",
                                "/api/theaters/{theaterId:[0-9]+}/auditoriums",
                                "/api/theaters/{theaterId:[0-9]+}/store/products",
                                "/api/screenings",
                                "/api/screenings/{screeningId:[0-9]+}",
                                "/api/screenings/{screeningId:[0-9]+}/seats",
                                "/api/store/products")
                            .permitAll()
                        .anyRequest()
                            .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, authenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }
}
