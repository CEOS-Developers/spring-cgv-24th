package com.ceos.cgv.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOrder;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

class PaymentResponsesTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PaymentJson json = new PaymentJson(mapper);
    private final PaymentResponses responses = new PaymentResponses(json);

    @Test
    void 기존_예매_결제_작업의_JSON을_같은_필드로_재생한다() {
        String stored = """
                {
                    "paymentId": 10,
                    "orderId": "legacy-reservation",
                    "reservationId": 12,
                    "paymentKey": "legacy-key",
                    "status": "FAILED",
                    "activeOperationId": null,
                    "totalAmount": 10000,
                    "balanceAmount": 10000,
                    "method": null,
                    "approvedAt": null,
                    "receiptUrl": null,
                    "cancellations": [],
                    "seats": [
                        {"reservedSeatId": 1, "seatRow": "A", "seatNumber": 1, "canceledAt": null}
                    ]
                }
                """;

        assertThat(mapper.readTree(json.write(json.result(stored)))).isEqualTo(mapper.readTree(stored));
    }

    @Test
    void 매점_결제_응답에는_매점_주문_ID만_있고_좌석_목록은_비어있다() {
        PaymentOrder order = PaymentOrder.forFoodOrder(
                "food-order", 12L, 20L, "팝콘", 6000, Instant.parse("2030-01-01T00:05:00Z"));
        Payment payment = Payment.builder().order(order).build();

        JsonNode orderBody = mapper.readTree(json.write(responses.order(payment)));
        JsonNode paymentBody = mapper.readTree(json.write(responses.payment(payment)));

        for (JsonNode body : List.of(orderBody, paymentBody)) {
            assertThat(body.get("foodOrderId").asLong()).isEqualTo(12L);
            assertThat(body.get("totalAmount").asLong()).isEqualTo(6000L);
            assertThat(body.has("reservationId")).isFalse();
            assertThat(body.has("seatPrice")).isFalse();
            assertThat(body.get("seats").isArray()).isTrue();
            assertThat(body.get("seats").size()).isZero();
        }
    }
}
