package com.ceos24.cgv.global.config;

import com.ceos24.cgv.global.filter.JwtAuthenticationFilter;
import com.ceos24.cgv.global.filter.LoginFilter;
import com.ceos24.cgv.global.handler.JwtAccessDeniedHandler;
import com.ceos24.cgv.global.handler.JwtAuthenticationEntryPoint;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final AuthenticationSuccessHandler loginSuccessHandler;
    private final AuthenticationFailureHandler loginFailureHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(
            AuthenticationConfiguration authenticationConfiguration,
            @Qualifier("LoginSuccessHandler") AuthenticationSuccessHandler loginSuccessHandler,
            @Qualifier("LoginFailureHandler") AuthenticationFailureHandler loginFailureHandler,
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    // 비밀번호 단방향(BCrypt) 암호화용 Bean
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // SecurityFilterChain
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        // CSRF 보안 필터 disable
        http
                .csrf(AbstractHttpConfigurer::disable);

        // CORS 설정
        /*http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));*/

        // 기본 Form 기반 인증 필터들 disable
        http
                .formLogin(AbstractHttpConfigurer::disable);

        //기본 Basic 인증 필터 disable
        http
                .httpBasic(AbstractHttpConfigurer::disable);

        // OAuth2 인증용
        /*http
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(socialSuccessHandler));*/

        // 인가
        http
                .authorizeHttpRequests(auth -> auth
                        // Swagger와 로그인
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/user/exist",
                                "/api/user",
                                "/login"
                        ).permitAll()

                        // 관리자 전용: 영화 등록 및 삭제
                        .requestMatchers(HttpMethod.POST, "/api/movies")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/movies/*")
                        .hasRole("ADMIN")

                        // 관리자 전용: 영화관 등록 및 비활성화
                        .requestMatchers(HttpMethod.POST, "/api/cinemas")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cinemas/*")
                        .hasRole("ADMIN")

                        // 관리자 전용: 상영관 및 상영정보 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/cinemas/*/auditoriums",
                                "/api/screenings"
                        ).hasRole("ADMIN")

                        // 공개 조회 API
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/movies/**",
                                "/api/cinemas/**",
                                "/api/screenings/**"
                        ).permitAll()

                        // 현재 로그인 사용자 전용 API
                        .requestMatchers("/api/me/**").authenticated()

                        // 그 외 API
                        .anyRequest().authenticated()
                );

        //예외처리
        http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                );

        // 세션 필터 설정(STATELESS)
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Access Token 검증 및 SecurityContext 인증 정보 설정
        http
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                );

        http
                .addFilterBefore(
                        new LoginFilter(
                                authenticationManager(authenticationConfiguration),
                                loginSuccessHandler,
                                loginFailureHandler
                        ),
                        UsernamePasswordAuthenticationFilter.class
                );


        // 기본 로그아웃 필터 + 커스텀 Refresh 토큰 삭제 핸들러 추가
        /*http
                .logout(logout -> logout
                        .addLogoutHandler(new RefreshTokenLogoutHandler(jwtService)));*/


        return http.build();
    }

    // 커스텀 자체 로그인 필터를 위한 AuthenticationManager Bean 수동 등록
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception{
        return configuration.getAuthenticationManager();
    }
}
