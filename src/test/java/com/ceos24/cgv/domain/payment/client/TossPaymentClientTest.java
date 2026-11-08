package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentClientTest {

	private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

	private MockRestServiceServer server;
	private TossPaymentClient tossPaymentClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
		server = MockRestServiceServer.bindTo(builder).build();
		tossPaymentClient = new TossPaymentClient(builder.build());
	}

	@Test
	void 승인_요청에_멱등키를_담아_보내고_모르는_필드는_무시하고_응답을_변환한다() {
		server.expect(requestTo(CONFIRM_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Idempotency-Key", "idem-1"))
				.andRespond(withSuccess("""
						{"mId":"tosspayments","paymentKey":"pk-1","orderId":"order-1","status":"DONE","totalAmount":15000,
						 "balanceAmount":15000,"method":"카드","approvedAt":"2026-11-03T15:00:00+09:00"}
						""", MediaType.APPLICATION_JSON));

		TossPaymentResponse response = tossPaymentClient.confirm("pk-1", "order-1", 15_000, "idem-1");

		assertThat(response.status()).isEqualTo("DONE");
		assertThat(response.totalAmount()).isEqualTo(15_000);
		assertThat(response.approvedAt()).isNotNull();
		server.verify();
	}

	@Test
	void 토스가_4xx로_거절하면_확정_실패로_구분한다() {
		server.expect(requestTo(CONFIRM_URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"한도초과 혹은 잔액부족으로 결제에 실패했습니다.\"}"));

		assertThatThrownBy(() -> tossPaymentClient.confirm("pk-1", "order-1", 15_000, "idem-1"))
				.isInstanceOfSatisfying(TossPaymentException.class, e -> {
					assertThat(e.isResultUnknown()).isFalse();
					assertThat(e.getTossCode()).isEqualTo("REJECT_CARD_PAYMENT");
				});
	}

	@Test
	void 같은_멱등키_요청이_처리중이면_결과_불명으로_구분한다() {
		server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"이전 멱등 요청이 처리중입니다.\"}"));

		assertThatThrownBy(() -> tossPaymentClient.confirm("pk-1", "order-1", 15_000, "idem-1"))
				.isInstanceOfSatisfying(TossPaymentException.class, e -> assertThat(e.isResultUnknown()).isTrue());
	}

	@Test
	void 토스가_5xx를_주면_결과_불명으로_구분한다() {
		server.expect(requestTo(CONFIRM_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> tossPaymentClient.confirm("pk-1", "order-1", 15_000, "idem-1"))
				.isInstanceOfSatisfying(TossPaymentException.class, e -> assertThat(e.isResultUnknown()).isTrue());
	}
}
