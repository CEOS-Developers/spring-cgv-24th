package com.ceos.cgv.domain.payment.demo;

import com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.GatewayPaymentStatus;
import com.ceos.cgv.domain.payment.gateway.PaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("payment-demo")
@TestPropertySource(properties = {
        "cgv.payment.toss.client-key=test_ck_demo_api_fixture",
        "cgv.payment.toss.secret-key=test_sk_demo_api_fixture"
})
class PaymentDemoApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PaymentGateway gateway;

    @Test
    void 데모_로그인부터_두좌석_승인과_부분_잔여취소를_외부호출없이_연결한다() throws Exception {
        for (String path : List.of("/payment-demo.html", "/payment-success.html", "/payment-fail.html",
                "/payment-demo.js", "/payment-demo.css")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"paymentdemo","password":"DemoPayment123!"}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String authorization = "Bearer " + JsonPath.<String>read(login, "$.data.accessToken");
        String movies = mockMvc.perform(get("/api/v1/movies")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number movieId = JsonPath.read(movies, "$.data[0].movieId");
        String screenings = mockMvc.perform(get("/api/v1/movies/" + movieId + "/screenings"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number screeningId = JsonPath.read(screenings, "$.data[0].screeningId");
        String hold = mockMvc.perform(post("/api/v1/seat-holds").header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"screeningId\":" + screeningId + ",\"seats\":[{\"seatRow\":\"A\",\"seatNumber\":1},{\"seatRow\":\"A\",\"seatNumber\":2}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number reservationId = JsonPath.read(hold, "$.data.reservationId");
        String orderResponse = mockMvc.perform(post("/api/v1/payment-orders").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reservationId\":" + reservationId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(orderResponse, "$.data.orderId");
        Number paymentId = JsonPath.read(orderResponse, "$.data.paymentId");
        Number total = JsonPath.read(orderResponse, "$.data.totalAmount");
        Number firstSeatId = JsonPath.read(orderResponse, "$.data.seats[0].reservedSeatId");
        AtomicReference<GatewayPayment> current = new AtomicReference<>();
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            ConfirmPaymentCommand command = invocation.getArgument(0);
            GatewayPayment result = GatewayPayment.builder().paymentKey(command.paymentKey())
                    .orderId(command.orderId()).totalAmount(command.amount()).balanceAmount(command.amount())
                    .status(GatewayPaymentStatus.DONE).method("카드").approvedAt(Instant.now()).build();
            current.set(result);
            return result;
        });
        when(gateway.cancel(any())).thenAnswer(invocation -> {
            CancelPaymentCommand command = invocation.getArgument(0);
            GatewayPayment before = current.get();
            long balance = before.balanceAmount() - command.cancelAmount();
            List<GatewayCancellation> cancellations = new ArrayList<>(before.cancellations());
            cancellations.add(new GatewayCancellation("demo-cancel-" + cancellations.size(),
                    command.cancelAmount(), Instant.now(), "DONE"));
            GatewayPayment result = new GatewayPayment(before.paymentKey(), before.orderId(), before.totalAmount(),
                    balance, balance == 0 ? GatewayPaymentStatus.CANCELED : GatewayPaymentStatus.PARTIAL_CANCELED,
                    before.method(), before.approvedAt(), null, cancellations);
            current.set(result);
            return result;
        });
        mockMvc.perform(post("/api/v1/payments/confirm").header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"demo-payment-fixture\",\"orderId\":\"" + orderId + "\",\"amount\":" + total + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancellations").header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservedSeatIds\":[" + firstSeatId + "],\"reason\":\"데모 부분 취소\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.status").value("PARTIALLY_CANCELED"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancellations").header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservedSeatIds\":null,\"reason\":\"데모 잔여 취소\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.balanceAmount").value(0));
        String finalPayment = mockMvc.perform(get("/api/v1/payments/" + paymentId).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andReturn().getResponse().getContentAsString();
        List<Object> canceledAt = JsonPath.read(finalPayment, "$.data.seats[*].canceledAt");
        assertThat(canceledAt).hasSize(2).doesNotContainNull();
        verify(gateway, times(1)).confirm(any());
        verify(gateway, times(2)).cancel(any());
    }
}
