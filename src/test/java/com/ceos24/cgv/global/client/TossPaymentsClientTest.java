package com.ceos24.cgv.global.client;

import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 토스를 실제로 부르지 않고 응답을 흉내 내 분류만 확인한다. 분류가 틀리면 돈이 빠진 결제를 실패로 기록하게 된다.
class TossPaymentsClientTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String CONFIRM_URL = BASE_URL + "/v1/payments/confirm";
    private static final String ORDER_ID = "cgv-r-0123456789abcdef0123456789abcdef";
    private static final String LOOKUP_URL = BASE_URL + "/v1/payments/orders/" + ORDER_ID;

    private MockRestServiceServer server;
    private TossPaymentsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentsClient(builder.build());
    }

    @Test
    void 승인되면_Approved로_결제_정보를_옮긴다() {
        server.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                // 같은 주문을 다시 보내도 토스가 두 번 결제하지 않게 하는 키다
                .andExpect(header("Idempotency-Key", ORDER_ID))
                .andExpect(jsonPath("$.paymentKey").value("tgen_test_key"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.amount").value(14000))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(result).isInstanceOf(Approved.class);
        TossPayment payment = ((Approved) result).payment();
        assertThat(payment.paymentKey()).isEqualTo("tgen_test_key");
        assertThat(payment.method()).isEqualTo("카드");
        assertThat(payment.totalAmount()).isEqualTo(14000);
        assertThat(payment.approvedAt()).isEqualTo(OffsetDateTime.parse("2024-06-01T09:05:00+09:00"));
        server.verify();
    }

    @Test
    void 카드사_거절은_Rejected로_코드와_메시지를_옮긴다() {
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다")));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(result).isEqualTo(new Rejected("REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다"));
    }

    @Test
    void 이미_승인된_결제라는_응답은_거절이_아니라_결과_불명이다() {
        // 앞선 승인 요청이 성공했는데 그 응답만 놓친 경우다. 거절로 처리하면 돈은 빠졌는데 실패로 기록된다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void 본문을_해석할_수_없는_4xx도_거절이다() {
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("bad request"));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(((Rejected) result).code()).isEqualTo("HTTP_400");
    }

    @Test
    void 서버_오류는_결과_불명이다() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withServerError());

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void 읽기_타임아웃은_결과_불명이다() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void DONE이_아닌_승인_응답은_결과_불명이다() {
        // 가상계좌는 입금 전까지 WAITING_FOR_DEPOSIT이다. 돈이 들어올지 아직 모른다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withSuccess(paymentJson("WAITING_FOR_DEPOSIT"), MediaType.APPLICATION_JSON));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    // ─── 조회 ─────────────────────────────────────────────────────────────────

    @Test
    void 조회되면_잔액과_취소_이력까지_옮긴다() {
        server.expect(requestTo(LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_test_key","orderId":"%s","status":"PARTIAL_CANCELED","method":"카드",
                         "totalAmount":28000,"balanceAmount":14000,"approvedAt":"2024-06-01T09:05:00+09:00",
                         "cancels":[{"transactionKey":"tx_1","cancelAmount":14000,"cancelReason":"좌석 취소",
                                     "cancelStatus":"DONE","canceledAt":"2024-06-01T09:30:00+09:00",
                                     "refundableAmount":14000}]}
                        """.formatted(ORDER_ID), MediaType.APPLICATION_JSON));

        TossLookupResult result = client.findByOrderId(ORDER_ID);

        assertThat(result).isInstanceOf(Found.class);
        TossPayment payment = ((Found) result).payment();
        assertThat(payment.status()).isEqualTo("PARTIAL_CANCELED");
        assertThat(payment.balanceAmount()).isEqualTo(14000);
        assertThat(payment.cancels()).singleElement().satisfies(cancel -> {
            assertThat(cancel.transactionKey()).isEqualTo("tx_1");
            assertThat(cancel.cancelAmount()).isEqualTo(14000);
            assertThat(cancel.cancelStatus()).isEqualTo("DONE");
        });
    }

    @Test
    void 취소_이력이_없으면_빈_목록이다() {
        // 토스는 취소가 없으면 cancels를 null로 준다
        server.expect(requestTo(LOOKUP_URL)).andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));

        TossPayment payment = ((Found) client.findByOrderId(ORDER_ID)).payment();

        assertThat(payment.cancels()).isEmpty();
        assertThat(payment.balanceAmount()).isEqualTo(14000);
    }

    @Test
    void 토스가_404면_NotFound다() {
        server.expect(requestTo(LOOKUP_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.")));

        assertThat(client.findByOrderId(ORDER_ID))
                .isEqualTo(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    }

    @Test
    void 인증_오류는_결제가_없다는_뜻이_아니라_조회_불가다() {
        server.expect(requestTo(LOOKUP_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.")));

        assertThat(client.findByOrderId(ORDER_ID)).isInstanceOf(Unavailable.class);
    }

    @Test
    void 조회_중_서버_오류나_타임아웃은_조회_불가다() {
        server.expect(requestTo(LOOKUP_URL)).andRespond(withServerError());
        assertThat(client.findByOrderId(ORDER_ID)).isInstanceOf(Unavailable.class);

        server.reset();
        server.expect(requestTo(LOOKUP_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(client.findByOrderId(ORDER_ID)).isInstanceOf(Unavailable.class);
    }

    private String paymentJson(String status) {
        // 토스 응답에는 우리가 받지 않는 필드가 많다. 모르는 필드가 있어도 역직렬화가 깨지지 않아야 한다
        return """
                {"mId":"tgen_docs","version":"2022-11-16","paymentKey":"tgen_test_key","orderId":"%s",
                 "orderName":"범죄도시4 1매","status":"%s","method":"카드","totalAmount":14000,"balanceAmount":14000,
                 "requestedAt":"2024-06-01T09:04:30+09:00","approvedAt":"2024-06-01T09:05:00+09:00",
                 "receipt":{"url":"https://dashboard.tosspayments.com/receipt"}}
                """.formatted(ORDER_ID, status);
    }

    private String errorJson(String code, String message) {
        return "{\"code\":\"%s\",\"message\":\"%s\"}".formatted(code, message);
    }
}
