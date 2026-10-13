package com.ceos.cgv.global.security.config;

import com.ceos.cgv.global.security.handler.RestAccessDeniedHandler;
import com.ceos.cgv.global.security.handler.RestAuthenticationEntryPoint;
import com.ceos.cgv.global.security.jwt.JwtAuthenticationFilter;
import com.ceos.cgv.global.security.jwt.JwtService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    private static final String PAYMENT_DEMO_PROFILE = "payment-demo";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   ObjectMapper objectMapper,
                                                   Environment environment) throws Exception {
        RequestMatcher publicApiMatcher = new PublicApiRequestMatcher(
                environment.acceptsProfiles(Profiles.of(PAYMENT_DEMO_PROFILE)));
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        RequestMatcher userApiMatcher = new OrRequestMatcher(
                pathPattern(POST, "/api/v1/movies/{movieId}/likes"),
                pathPattern(POST, "/api/v1/cinemas/{cinemaId}/likes"),
                pathPattern(GET, "/api/v1/reservations/{reservationId}"),
                pathPattern(DELETE, "/api/v1/reservations/{reservationId}"),
                pathPattern(POST, "/api/v1/food-orders"),
                pathPattern(GET, "/api/v1/food-orders/{orderId}"),
                pathPattern(GET, "/api/v1/payment-config"),
                pathPattern(POST, "/api/v1/payment-orders"),
                pathPattern(GET, "/api/v1/payment-orders/{orderId}"),
                pathPattern(POST, "/api/v1/payments/confirm"),
                pathPattern(GET, "/api/v1/payments/{paymentId}"),
                pathPattern(POST, "/api/v1/payments/{paymentId}/cancellations"),
                pathPattern(GET, "/api/v1/payment-operations/{operationId}"),
                pathPattern(POST, "/api/v1/payment-operations/{operationId}/reconcile"),
                pathPattern(POST, "/api/v1/seat-holds"),
                pathPattern(DELETE, "/api/v1/seat-holds/{reservationId}"));
        RequestMatcher adminApiMatcher = new OrRequestMatcher(
                pathPattern(POST, "/api/v1/movies"),
                pathPattern(DELETE, "/api/v1/movies/{movieId}"),
                pathPattern(POST, "/api/v1/screens"),
                pathPattern(POST, "/api/v1/screenings"),
                pathPattern(POST, "/api/v1/products"),
                pathPattern(POST, "/api/v1/inventories"));

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicApiMatcher).permitAll()
                        .requestMatchers(userApiMatcher).authenticated()
                        .requestMatchers(adminApiMatcher).hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, entryPoint, publicApiMatcher),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
