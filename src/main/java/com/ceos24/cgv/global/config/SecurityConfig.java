package com.ceos24.cgv.global.config;

import com.ceos24.cgv.global.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider authenticationProvider =
                new DaoAuthenticationProvider(customUserDetailsService);

        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider daoAuthenticationProvider
    ) {
        return new ProviderManager(daoAuthenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider daoAuthenticationProvider
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .formLogin(formLogin -> formLogin.disable())

                .httpBasic(httpBasic -> httpBasic.disable())

                .authenticationProvider(daoAuthenticationProvider)

                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                );

        return http.build();
    }
}
