package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.global.security.jwt.JwtAccessDeniedHandler;
import com.ceos24.cgv.global.security.jwt.JwtAuthenticationEntryPoint;
import com.ceos24.cgv.global.security.jwt.JwtAuthenticationFilter;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    /**
     * 관리 포트(Actuator 전용, 기본 8081)로 들어온 요청은 인증 없이 허용한다.
     * 관리 포트에도 앱의 보안 설정이 그대로 적용되어 Prometheus 수집이 401로 막히기 때문이다.
     * 이 포트는 서비스 포트(8080)와 달리 외부에 연결하지 않으며, 노출된 엔드포인트는 health/info/prometheus뿐이다.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain managementFilterChain(
            HttpSecurity http, @Value("${management.server.port:8081}") int managementPort) throws Exception {
        http.securityMatcher(request -> request.getLocalPort() == managementPort
                        && request.getRequestURI().startsWith("/actuator"))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/reissue")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook")
                        .permitAll() // 토스 웹훅 수신 엔드포인트 허용 (시그니처 및 단건 조회로 검증)
                        .requestMatchers(HttpMethod.GET, "/api/movies")
                        .permitAll() // 전체 영화 조회
                        .requestMatchers(HttpMethod.GET, "/api/screenings/{screeningId}")
                        .permitAll() // 상영 정보 상세 조회
                        .requestMatchers(HttpMethod.GET, "/api/screenings/{screeningId}/seats")
                        .permitAll() // 특정 상영 좌석 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/screens")
                        .permitAll() // 상영관 목록 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/screenings")
                        .permitAll() // 영화관별 상영 시간표 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/movies")
                        .permitAll() // 영화관별 영화 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters")
                        .permitAll() // 전체 영화관 목록 조회
                        .requestMatchers(HttpMethod.GET, "/api/theaters/{theaterId}/stores")
                        .permitAll() // 매장 정보 조회
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "Refresh-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
