package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.support.ControllerIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWidgetControllerTest extends ControllerIntegrationTest {

    @Test
    void 결제위젯_설정은_토큰_없이_클라이언트_키만_준다() throws Exception {
        String body = mockMvc.perform(get("/api/payments/widget-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientKey").value("test_gck_dummy-for-test-only"))
                .andReturn().getResponse().getContentAsString();

        // 시크릿 키가 섞여 나가면 누구나 우리 가맹점으로 승인·취소를 할 수 있다
        assertThat(body).doesNotContain("test_gsk_");
    }

    @Test
    void 결제_테스트_페이지는_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/payment-test.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("js.tosspayments.com/v2/standard")));
    }

    @Test
    void 토스가_successUrl로_되돌려_보낸_요청도_토큰_없이_열린다() throws Exception {
        // 결제창에서 돌아오는 것은 브라우저 이동이라 Authorization 헤더가 없다
        mockMvc.perform(get("/payment-test.html")
                        .param("paymentKey", "tgen_test_key")
                        .param("orderId", "cgv-r-0123456789abcdef0123456789abcdef")
                        .param("amount", "14000"))
                .andExpect(status().isOk());
    }
}
