package com.ceos.cgv.domain.payment.controller;

import com.ceos.cgv.domain.payment.demo.PaymentDemoInitializer;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "cgv.payment.toss.client-key=test_ck_client_config_fixture",
        "cgv.payment.toss.secret-key=test_sk_client_config_fixture"
})
class PaymentClientConfigControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.ceos.cgv.global.security.jwt.JwtService jwtService;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 인증이_없으면_클라이언트_설정도_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/payment-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_클라이언트에는_테스트_클라이언트키와_통화만_반환한다() throws Exception {
        String token = jwtService.issue(1L, com.ceos.cgv.domain.user.enums.UserRole.USER);
        mockMvc.perform(get("/api/v1/payment-config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.clientKey").value("test_ck_client_config_fixture"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.secretKey").doesNotExist());
        assertThat(applicationContext.getBeansOfType(PaymentDemoInitializer.class)).isEmpty();
        assertThat(new PaymentClientConfigController(new TossPaymentProperties(
                "test_ck_fixture", "test_sk_fixture", null, null)).config().getBody().data().toString())
                .doesNotContain("test_ck_fixture", "test_sk_fixture");
    }

    @Test
    void 누락되거나_라이브인_키는_기존_공통_오류로_변환한다() {
        for (String[] keys : new String[][]{
                {"", "test_sk_fixture"}, {"test_ck_fixture", ""},
                {"live_ck_fixture", "test_sk_fixture"}, {"test_ck_fixture", "live_sk_fixture"}}) {
            PaymentClientConfigController controller = new PaymentClientConfigController(
                    new TossPaymentProperties(keys[0], keys[1], null, null));
            assertThatThrownBy(controller::config)
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        }
    }
}
