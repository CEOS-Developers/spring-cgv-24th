package com.ceos24.cgv.global.config;

import com.ceos24.cgv.global.security.CustomAccessDeniedHandler;
import com.ceos24.cgv.global.security.CustomAuthenticationEntryPoint;
import com.ceos24.cgv.global.security.CustomUserDetailsService;
import com.ceos24.cgv.global.security.jwt.JwtAuthenticationFilter;
import com.ceos24.cgv.global.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {


    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        // 회원을 조회할 서비스 연결
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        // 비밀번호를 검증할 인코더 연결
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider
    ) {
        // 로그인 인증 요청을 위 Provider에 위임
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider jwtTokenProvider,
            CustomAuthenticationEntryPoint entryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        // Security 체인 안에서 사용할 필터를 직접 생성
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, entryPoint);

        http
                // Bearer 헤더로만 인증하며 인증 쿠키를 사용하지 않음
                .csrf(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth

                        // 회원가입·로그인
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login"
                        ).permitAll()

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 관리자 전용
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 공개 영화 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/movies",
                                "/api/movies/{movieId}"
                        ).permitAll()

                        // 영화 등록·삭제는 관리자만 허용
                        .requestMatchers(HttpMethod.POST, "/api/movies")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/movies/{movieId}"
                        ).hasRole("ADMIN")

                        // 나머지 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}