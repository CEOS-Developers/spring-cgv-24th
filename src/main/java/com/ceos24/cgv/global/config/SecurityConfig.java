package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.global.security.jwt.JwtAccessDeniedHandler;
import com.ceos24.cgv.global.security.jwt.JwtAuthenticationEntryPoint;
import com.ceos24.cgv.global.security.jwt.JwtAuthenticationFilter;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies")
                        .permitAll() // 전체 영화 조회
                        .requestMatchers(HttpMethod.GET, "/api/screenings/{screeningId}")
                        .permitAll() // 상영 정보 상세 조회
                        .requestMatchers(HttpMethod.GET, "/api/screenings/{screeningId}/seats")
                        .permitAll() // 특정 상영 좌석 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/screens")
                        .permitAll() // 상영관 목록 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/screenings")
                        .permitAll() // 영화관별 상영 시간표 조
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/movies")
                        .permitAll() // 영화관별 영화 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters")
                        .permitAll() // 전체 영화관 목록 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/stores")
                        .permitAll() // 매장 정보 조회
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, memberRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
