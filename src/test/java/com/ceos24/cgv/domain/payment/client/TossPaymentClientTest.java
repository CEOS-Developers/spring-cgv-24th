package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TossPaymentClientTest {

    @Test
    void newPaymentKeyGetsNewApprovalIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.tosspayments.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = new TossPaymentClient(builder.build());

        server.expect(once(), requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header("Idempotency-Key", "confirm-new-key"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.confirm(
                new PaymentConfirmRequest("new-key", "order-123", 10000)))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }
}
