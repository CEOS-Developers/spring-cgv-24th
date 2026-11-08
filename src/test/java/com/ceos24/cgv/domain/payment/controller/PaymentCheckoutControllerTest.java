package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.client.TossPaymentProperties;
import com.ceos24.cgv.global.config.SecurityConfig;
import com.ceos24.cgv.global.exception.handler.GlobalExceptionHandler;
import com.ceos24.cgv.global.handler.JwtAccessDeniedHandler;
import com.ceos24.cgv.global.handler.JwtAuthenticationEntryPoint;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentCheckoutControllerTest {

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).apply(springSecurity()).build();
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    @Test
    void 인증없이_리다이렉트화면과_스크립트를_읽을수있다() throws Exception {
        mvc.perform(get("/payment-demo/index.html?flow=success&orderId=cgv_store_test&amount=10000&paymentKey=test"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("토스 결제창 열기")));
        mvc.perform(get("/payment-demo/payment-flow.mjs"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("javascript")));
    }

    @Test
    void 화면을_공개해도_설정과_승인과_주문생성은_인증이_필요하다() throws Exception {
        mvc.perform(get("/api/me/payments/checkout-config")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/payments/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/purchases")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payment-demo/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인한_사용자에게_공개키만_반환하고_시크릿키는_노출하지않는다() throws Exception {
        mvc.perform(get("/api/me/payments/checkout-config").with(user("buyer")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.result.clientKey").value("test_ck_demo_public"))
                .andExpect(jsonPath("$.result", aMapWithSize(1)))
                .andExpect(content().string(not(containsString("test_sk_demo_private"))));
    }

    @Test
    void 라이브키와_위젯용키와_누락된키는_테스트결제창에서_거절한다() throws Exception {
        for (String[] keys : new String[][] {
                {"live_ck_public", "live_sk_private"}, {"test_gck_widget", "test_gsk_widget"},
                {null, "test_sk_private"}, {"test_ck_public", "live_sk_private"}
        }) {
            var controller = new PaymentCheckoutController(properties(keys[0], keys[1]));
            MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
                    .perform(get("/api/me/payments/checkout-config"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.divisionCode").value("PAY013"));
        }
    }

    private static TossPaymentProperties properties(String clientKey, String secretKey) {
        return new TossPaymentProperties(clientKey, secretKey, URI.create("https://api.tosspayments.com"),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, PaymentCheckoutController.class, GlobalExceptionHandler.class,
            JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean TossPaymentProperties properties() { return PaymentCheckoutControllerTest.properties("test_ck_demo_public", "test_sk_demo_private"); }
        @Bean JwtTokenProvider jwtTokenProvider() { return mock(JwtTokenProvider.class); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean UserDetailsService userDetailsService() { return new InMemoryUserDetailsManager(); }
        @Bean("LoginSuccessHandler") AuthenticationSuccessHandler loginSuccessHandler() { return mock(AuthenticationSuccessHandler.class); }
        @Bean("LoginFailureHandler") AuthenticationFailureHandler loginFailureHandler() { return mock(AuthenticationFailureHandler.class); }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/payment-demo/**").addResourceLocations("classpath:/static/payment-demo/");
        }
    }
}
