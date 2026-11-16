package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.concession.scheduler.FoodOrderCheckoutCleanupScheduler;
import com.ceos.cgv.domain.concession.service.payment.FoodOrderPaymentService;
import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.enums.PaymentStatus;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.support.PaymentIntegrationFixture;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FoodPaymentIntegrationTest extends PaymentIntegrationFixture {
    private static final String CHECKOUT_PATH = "/api/v1/food-orders/checkout";
    private static final String CHECKOUT = """
            {"cinemaId":8912,"items":[{"productId":8920,"quantity":2},{"productId":8921,"quantity":1}]}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private FoodOrderPaymentService foodOrderPayments;
    @Autowired private FoodOrderCheckoutCleanupScheduler cleanup;

    @BeforeEach
    void seedInventory() {
        jdbc.update("INSERT INTO products(product_id,name,price,description) VALUES "
                + "(8920,'팝콘',1200,'매점'),(8921,'콜라',800,'매점')");
        jdbc.update("INSERT INTO inventories(inventory_id,cinema_id,product_id,stock_quantity) VALUES "
                + "(8922,8912,8920,5),(8923,8912,8921,5)");
    }

    @Test
    void 인증된_매점_체크아웃은_가격을_스냅샷하고_재고를_한번만_확보한다() throws Exception {
        UUID key = UUID.randomUUID();
        mvc.perform(post(CHECKOUT_PATH).contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)
                        .header("Idempotency-Key", key))
                .andExpect(status().isUnauthorized());
        requestCheckout(key, CHECKOUT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.foodOrderId").isNumber())
                .andExpect(jsonPath("$.data.totalAmount").value(3200));
        requestCheckout(key, """
                {"cinemaId":8912,"items":[{"productId":8921,"quantity":1},
                {"productId":8920,"quantity":1},{"productId":8920,"quantity":1}]}
                """)
                .andExpect(status().isOk());
        requestCheckout(key, CHECKOUT.replace("\"quantity\":2", "\"quantity\":3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOOD_ORDER_CHECKOUT_CONFLICT"));
        assertThat(stock(8920)).isEqualTo(3);
        assertThat(stock(8921)).isEqualTo(4);
    }

    @Test
    void 동일키_동시_체크아웃은_결제_주문도_재고_확보도_한번만_만든다() throws Exception {
        UUID key = UUID.randomUUID();
        List<PaymentOrderResponse> orders = concurrent(() -> checkout(key), () -> checkout(key));
        assertThat(orders.getFirst().orderId()).isEqualTo(orders.getLast().orderId());
        assertThat(stock(8920)).isEqualTo(3);
        assertThat(stock(8921)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_order WHERE food_order_id IS NOT NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 같은_회원의_기존_주문과_유료_주문은_동시에_재고를_확보할_수_있다() throws Exception {
        var statuses = concurrent(
                () -> requestCheckout(UUID.randomUUID(), CHECKOUT)
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(post("/api/v1/food-orders").contentType(MediaType.APPLICATION_JSON)
                                .content(CHECKOUT).header("Authorization", token(USER_ID)))
                        .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                        .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactly(201, 201);
        assertThat(stock(8920)).isEqualTo(1);
        assertThat(stock(8921)).isEqualTo(3);
    }

    @Test
    void 공통_승인과_전체취소_API가_매점_주문을_확정하고_재고를_한번만_반환한다() throws Exception {
        PaymentOrderResponse order = checkout(UUID.randomUUID());
        GatewayPayment approved = done(order);
        when(gateway.confirm(any())).thenAnswer(call -> {
            assertOutsideTransaction();
            assertThat(foodStatus(order)).isEqualTo("PAYMENT_PENDING");
            return approved;
        });
        var confirmRequest = approvalRequest(approved);
        assertThatThrownBy(() -> service.confirm(8918L, UUID.randomUUID(), confirmRequest))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.confirm(USER_ID, UUID.randomUUID(),
                new PaymentConfirmRequest(approved.paymentKey(), order.orderId(), 1L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
        UUID confirmKey = UUID.randomUUID();
        mvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", token(USER_ID)).header("Idempotency-Key", confirmKey)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.foodOrderId").value(order.foodOrderId()));
        mvc.perform(get("/api/v1/food-orders/{id}", order.foodOrderId())
                        .header("Authorization", token(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        assertThat(stock(8920)).isEqualTo(3);

        mvc.perform(post("/api/v1/payments/{id}/cancellations", order.paymentId())
                        .header("Authorization", token(USER_ID)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservedSeatIds\":[1],\"reason\":\"부분 취소\"}"))
                .andExpect(status().isBadRequest());
        when(gateway.cancel(any())).thenAnswer(call -> {
            assertOutsideTransaction();
            assertThat(stock(8920)).isEqualTo(3);
            return canceled(approved, order.totalAmount());
        });
        UUID cancelKey = UUID.randomUUID();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/payments/{id}/cancellations", order.paymentId())
                            .header("Authorization", token(USER_ID)).header("Idempotency-Key", cancelKey)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"전체 취소\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result.status").value("CANCELED"))
                    .andExpect(jsonPath("$.data.result.balanceAmount").value(0));
        }
        verify(gateway, times(1)).confirm(any());
        verify(gateway, times(1)).cancel(any());
        assertThat(foodStatus(order)).isEqualTo("CANCELED");
        assertThat(stock(8920)).isEqualTo(5);
        assertThat(stock(8921)).isEqualTo(5);
    }

    @Test
    void 승인_거절은_재고를_반환하고_같은_작업을_재생한다() throws Exception {
        PaymentOrderResponse order = checkout(UUID.randomUUID());
        when(gateway.confirm(any())).thenThrow(new PaymentGatewayException(GatewayFailureType.REJECTED, "DECLINED"));
        UUID key = UUID.randomUUID();
        var request = approvalRequest(done(order));
        var failed = service.confirm(USER_ID, key, request);
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(service.confirm(USER_ID, key, request).operationId()).isEqualTo(failed.operationId());
        assertThat(foodStatus(order)).isEqualTo("RELEASED");
        verify(gateway, times(1)).confirm(any());
        assertThat(stock(8920)).isEqualTo(5);
        assertThat(stock(8921)).isEqualTo(5);
    }

    @Test
    void 승인과_취소_타임아웃은_재고를_보존하고_복구에서만_변경한다() throws Exception {
        PaymentOrderResponse order = checkout(UUID.randomUUID());
        GatewayPayment approved = done(order);
        when(gateway.confirm(any())).thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        var pending = service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(approved));
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(foodStatus(order)).isEqualTo("PAYMENT_PENDING");
        elapseHold(order);
        assertThat(foodOrderPayments.expireIfElapsed(order.foodOrderId())).isFalse();
        mvc.perform(delete("/api/v1/food-orders/{id}/checkout", order.foodOrderId())
                        .header("Authorization", token(USER_ID)))
                .andExpect(status().isConflict());
        assertThat(stock(8920)).isEqualTo(3);
        when(gateway.getPayment(approved.paymentKey())).thenReturn(approved);
        assertThat(service.reconcile(USER_ID, pending.operationId()).status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(foodStatus(order)).isEqualTo("PAID");

        when(gateway.cancel(any())).thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        var refund = service.cancel(USER_ID, order.paymentId(), UUID.randomUUID(), new PaymentCancelRequest(null, "전체 취소"));
        assertThat(refund.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(foodStatus(order)).isEqualTo("PAID");
        assertThat(stock(8920)).isEqualTo(3);
        when(gateway.getPayment(approved.paymentKey())).thenReturn(canceled(approved, order.totalAmount()));
        var recovered = service.reconcile(USER_ID, refund.operationId());
        assertThat(recovered.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(service.reconcile(USER_ID, refund.operationId()).operationId()).isEqualTo(recovered.operationId());
        assertThat(foodStatus(order)).isEqualTo("CANCELED");
        assertThat(stock(8920)).isEqualTo(5);
        assertThat(stock(8921)).isEqualTo(5);
    }

    @Test
    void 승인_전_해제와_만료는_PG_호출_없이_재고를_반환한다() throws Exception {
        PaymentOrderResponse released = checkout(UUID.randomUUID());
        mvc.perform(delete("/api/v1/food-orders/{id}/checkout", released.foodOrderId())
                        .header("Authorization", token(8918L)))
                .andExpect(status().isForbidden());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete("/api/v1/food-orders/{id}/checkout", released.foodOrderId())
                            .header("Authorization", token(USER_ID)))
                    .andExpect(status().isNoContent());
        }
        assertThat(stock(8920)).isEqualTo(5);
        PaymentOrderResponse elapsed = checkout(UUID.randomUUID());
        elapseHold(elapsed);
        assertThatThrownBy(() -> service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(done(elapsed))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOOD_ORDER_EXPIRED));
        cleanup.cleanup();
        assertThat(foodOrderPayments.expireIfElapsed(elapsed.foodOrderId())).isFalse();
        assertThat(foodStatus(elapsed)).isEqualTo("EXPIRED");
        assertThat(stock(8920)).isEqualTo(5);
        assertThat(stock(8921)).isEqualTo(5);
        // verifyReady only checks configuration and never contacts the PG.
        verify(gateway, times(0)).confirm(any());
        verify(gateway, times(0)).cancel(any());
    }

    private ResultActions requestCheckout(UUID key, String body) throws Exception {
        return mvc.perform(post(CHECKOUT_PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Idempotency-Key", key).header("Authorization", token(USER_ID)));
    }

    private PaymentOrderResponse checkout(UUID key) throws Exception {
        String body = requestCheckout(key, CHECKOUT).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return mapper.treeToValue(mapper.readTree(body).get("data"), PaymentOrderResponse.class);
    }

    private void elapseHold(PaymentOrderResponse order) {
        jdbc.update("UPDATE food_orders SET hold_expires_at=TIMESTAMPADD(SECOND, -600, hold_expires_at)"
                + " WHERE order_id=?", order.foodOrderId());
    }

    private String foodStatus(PaymentOrderResponse order) {
        return jdbc.queryForObject("SELECT payment_status FROM food_orders WHERE order_id=?",
                String.class, order.foodOrderId());
    }

    private String token(Long userId) {
        return "Bearer " + jwt.issue(userId, UserRole.USER);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id=?",
                Integer.class, productId);
    }
}
