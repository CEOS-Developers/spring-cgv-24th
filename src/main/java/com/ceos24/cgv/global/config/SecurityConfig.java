package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.user.entity.UserRoleType;
import com.ceos24.cgv.global.filter.LoginFilter;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final AuthenticationSuccessHandler loginSuccessHandler;

    public SecurityConfig(
            AuthenticationConfiguration authenticationConfiguration,
            @Qualifier("LoginSuccessHandler") AuthenticationSuccessHandler loginSuccessHandler
    ) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.loginSuccessHandler = loginSuccessHandler;
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
                        .requestMatchers("/jwt/exchange", "/jwt/refresh","/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/user/exist", "/api/user","/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user").hasRole(UserRoleType.USER.name())
                        .requestMatchers(HttpMethod.PUT, "/user").hasRole(UserRoleType.USER.name())
                        .requestMatchers(HttpMethod.DELETE, "/user").hasRole(UserRoleType.USER.name())
                        .anyRequest().authenticated()
                );

        //예외처리
        http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED); // 401 응답
                        })
                        .accessDeniedHandler((request, response, authException) -> {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN); // 403 응답
                        })
                );

        // 세션 필터 설정(STATELESS)
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        //커스텀 필터 추가
        /*http
                .addFilterBefore(new JWTFilter(), LogoutFilter.class);*/

        http
                .addFilterBefore(new LoginFilter(authenticationManager(authenticationConfiguration),loginSuccessHandler), UsernamePasswordAuthenticationFilter.class);


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
