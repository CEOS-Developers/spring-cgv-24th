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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
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
    private static final String CANCEL_URL = BASE_URL + "/v1/payments/tgen_test_key/cancel";
    private static final String CANCEL_KEY = "0123456789abcdef0123456789abcdef";

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

        assertThat(result).isEqualTo(new Rejected("REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다", false));
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
    void 같은_주문의_첫_승인_요청이_처리_중이면_결과_불명이다() {
        // 첫 요청은 승인될 수 있다. 거절로 확정하면 돈이 빠진 회원이 새 주문으로 다시 결제할 수 있다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("IDEMPOTENT_REQUEST_PROCESSING", "이전 요청이 처리 중입니다.")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void 서버_오류의_사유에는_응답_본문이_아니라_상태와_오류_코드만_남긴다() {
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다.")));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(((Unknown) result).reason()).isEqualTo("HTTP 500 FAILED_INTERNAL_SYSTEM_PROCESSING");
    }

    @Test
    void 연결_오류의_사유에는_요청_주소가_남지_않는다() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(((Unknown) result).reason()).isEqualTo("SocketTimeoutException: Read timed out");
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

    @Test
    void 결제사_일시_오류는_승인에서도_결과_불명이다() {
        // 카드사에서 처리됐는지 알 수 없다. 거절로 확정하면 회원이 새 주문으로 다시 결제해 두 번 빠질 수 있다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("PROVIDER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void 키_오류는_토스가_판단하지_않은_거절이다() {
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000))
                .isEqualTo(new Rejected("UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.", true));
    }

    @Test
    void 호출_한도_초과는_토스가_판단하지_않은_거절이다() {
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.TEXT_PLAIN).body("slow down"));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(((Rejected) result).serviceError()).isTrue();
    }

    @Test
    void 연결에_실패하면_같은_멱등키로_한_번만_다시_보낸다() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(CONFIRM_URL))
                .andExpect(header("Idempotency-Key", ORDER_ID))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Approved.class);
        server.verify();
    }

    @Test
    void 두_번_모두_연결하지_못한_승인은_토스에_닿지_않았으므로_거절이다() {
        // 승인은 주문마다 한 번만 보낸다. 연결조차 못 했으면 이 주문의 승인은 토스에 한 번도 닿지 않았다
        server.expect(ExpectedCount.times(2), requestTo(CONFIRM_URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        TossConfirmResult result = client.confirm("tgen_test_key", ORDER_ID, 14000);

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(((Rejected) result).code()).isEqualTo("CONNECTION_FAILED");
        assertThat(((Rejected) result).serviceError()).isTrue();
        server.verify();
    }

    @Test
    void 연결_후_재시도에서_읽기_타임아웃이면_결과_불명이다() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(CONFIRM_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
        server.verify();
    }

    @Test
    void 읽기_타임아웃은_다시_보내지_않는다() {
        // 이미 최대 60초를 기다렸다. 두 번째 요청이 오면 MockRestServiceServer가 실패시킨다
        server.expect(ExpectedCount.once(), requestTo(CONFIRM_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
        server.verify();
    }

    @Test
    void 승인_응답의_금액이나_주문이_요청과_다르면_결과_불명이다() {
        // 2xx라도 다른 결제의 응답으로 확정할 수 없다. 돈은 움직였을 수 있으므로 거절도 아니다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));
        assertThat(client.confirm("tgen_test_key", ORDER_ID, 28000)).isInstanceOf(Unknown.class);

        server.reset();
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withSuccess(paymentJson("DONE").replace(ORDER_ID, "cgv-r-other"), MediaType.APPLICATION_JSON));
        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);

        server.reset();
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));
        assertThat(client.confirm("tgen_other_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    @Test
    void 성공_응답이지만_JSON이_아니면_결과_불명이다() {
        // 프록시가 끼어 HTML을 돌려주는 경우 등. 토스가 승인했는지 알 수 없다
        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertThat(client.confirm("tgen_test_key", ORDER_ID, 14000)).isInstanceOf(Unknown.class);
    }

    // ─── 조회 ─────────────────────────────────────────────────────────────────

    @Test
    void 다른_결제의_조회_응답은_조회_불가다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/tgen_other_key"))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));

        assertThat(client.findByPaymentKey("tgen_other_key")).isInstanceOf(Unavailable.class);
    }

    @Test
    void 조회는_연결에_실패하면_한_번_다시_보내고_그래도_실패하면_조회_불가다() {
        server.expect(ExpectedCount.times(2), requestTo(LOOKUP_URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThat(client.findByOrderId(ORDER_ID)).isInstanceOf(Unavailable.class);
        server.verify();
    }

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
    void 상점을_찾지_못했다는_404는_결제_없음이_아니라_조회_불가다() {
        // 문서 공용 테스트 키는 실제로 있는 결제도 orderId로 조회하면 이 응답을 준다(결제 세션 4에서 확인).
        // 결제 없음으로 읽으면 승인된 결제를 실패로 확정하게 된다
        server.expect(requestTo(LOOKUP_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("NOT_FOUND_MERCHANT", "존재하지 않는 상점 정보 입니다.")));

        assertThat(client.findByOrderId(ORDER_ID)).isInstanceOf(Unavailable.class);
    }

    @Test
    void paymentKey로_조회한다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/tgen_test_key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paymentJson("DONE"), MediaType.APPLICATION_JSON));

        assertThat(client.findByPaymentKey("tgen_test_key")).isInstanceOf(Found.class);
        server.verify();
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

    // ─── 취소 ─────────────────────────────────────────────────────────────────

    @Test
    void 취소되면_이번_취소_거래와_남은_잔액을_옮긴다() {
        server.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                // 취소 요청마다 다른 키다. orderId를 쓰면 두 번째 부분 취소에 첫 응답이 돌아온다
                .andExpect(header("Idempotency-Key", CANCEL_KEY))
                .andExpect(jsonPath("$.cancelAmount").value(14000))
                .andExpect(jsonPath("$.cancelReason").value("예매 부분 취소: A2"))
                .andRespond(withSuccess(cancelJson("PARTIAL_CANCELED", 28000, "DONE"), MediaType.APPLICATION_JSON));

        TossCancelResult result = client.cancel("tgen_test_key", CANCEL_KEY, 14000, "예매 부분 취소: A2");

        assertThat(result).isInstanceOf(TossCancelResult.Canceled.class);
        TossCancelResult.Canceled canceled = (TossCancelResult.Canceled) result;
        assertThat(canceled.payment().balanceAmount()).isEqualTo(28000);
        // 앞선 취소가 cancels에 먼저 있다. 이번 요청의 거래는 마지막 항목이다
        assertThat(canceled.cancel().transactionKey()).isEqualTo("tx_2");
        assertThat(canceled.cancel().cancelAmount()).isEqualTo(14000);
        server.verify();
    }

    @Test
    void 취소_불가_응답은_거절이다() {
        server.expect(requestTo(CANCEL_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다.")));

        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유"))
                .isEqualTo(new TossCancelResult.Rejected("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다."));
    }

    @Test
    void 이미_취소됐다는_응답은_거절이_아니라_결과_불명이다() {
        // 앞선 취소가 성공했는데 그 응답만 놓쳤을 수 있다
        assertCancelUnknownOn(HttpStatus.BAD_REQUEST, "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다.");
    }

    @Test
    void 같은_멱등키의_첫_요청이_처리_중이면_결과_불명이다() {
        assertCancelUnknownOn(HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING", "이전 요청이 처리 중입니다.");
    }

    @Test
    void 결제사_일시_오류는_400이어도_결과_불명이다() {
        // 거절로 보면 회원이 다시 요청할 때 새 멱등키로 같은 금액이 한 번 더 환불될 수 있다
        assertCancelUnknownOn(HttpStatus.BAD_REQUEST, "PROVIDER_ERROR", "일시적인 오류가 발생했습니다.");
    }

    @Test
    void 취소_중_서버_오류나_타임아웃은_결과_불명이다() {
        server.expect(requestTo(CANCEL_URL)).andRespond(withServerError());
        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);

        server.reset();
        server.expect(requestTo(CANCEL_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);
    }

    @Test
    void 취소_거래가_DONE이_아니면_결과_불명이다() {
        server.expect(requestTo(CANCEL_URL))
                .andRespond(withSuccess(cancelJson("PARTIAL_CANCELED", 28000, "IN_PROGRESS"), MediaType.APPLICATION_JSON));

        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);
    }

    @Test
    void 취소_응답의_금액이_요청과_다르면_결과_불명이다() {
        // 마지막 거래가 14,000원인데 7,000원을 요청했다. 이 거래로 확정하면 누적 취소액과 실제 환불이 어긋난다
        server.expect(requestTo(CANCEL_URL))
                .andRespond(withSuccess(cancelJson("PARTIAL_CANCELED", 28000, "DONE"), MediaType.APPLICATION_JSON));

        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 7000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);
    }

    @Test
    void 취소에서는_키_오류도_결과_불명이다() {
        // 취소는 같은 멱등키의 재시도일 수 있다. 이번 요청이 막혔다고 첫 요청이 환불되지 않았다는 뜻은 아니다
        assertCancelUnknownOn(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.");
    }

    @Test
    void 취소에서는_두_번_모두_연결하지_못해도_결과_불명이다() {
        server.expect(ExpectedCount.times(2), requestTo(CANCEL_URL))
                .andExpect(header("Idempotency-Key", CANCEL_KEY))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);
        server.verify();
    }

    private void assertCancelUnknownOn(HttpStatus status, String code, String message) {
        server.expect(requestTo(CANCEL_URL))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorJson(code, message)));

        assertThat(client.cancel("tgen_test_key", CANCEL_KEY, 14000, "사유")).isInstanceOf(TossCancelResult.Unknown.class);
    }

    private String cancelJson(String status, int balanceAmount, String lastCancelStatus) {
        return """
                {"paymentKey":"tgen_test_key","orderId":"%s","status":"%s","method":"카드",
                 "totalAmount":42000,"balanceAmount":%d,"approvedAt":"2024-06-01T09:05:00+09:00",
                 "cancels":[
                   {"transactionKey":"tx_1","cancelAmount":14000,"cancelReason":"예매 부분 취소: A1",
                    "cancelStatus":"DONE","canceledAt":"2024-06-01T09:20:00+09:00"},
                   {"transactionKey":"tx_2","cancelAmount":14000,"cancelReason":"예매 부분 취소: A2",
                    "cancelStatus":"%s","canceledAt":"2024-06-01T09:30:00+09:00"}]}
                """.formatted(ORDER_ID, status, balanceAmount, lastCancelStatus);
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
