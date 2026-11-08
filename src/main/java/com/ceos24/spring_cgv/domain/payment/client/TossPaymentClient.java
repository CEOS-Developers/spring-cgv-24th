package com.ceos24.spring_cgv.domain.payment.client;

import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentCancelRequest;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentCancelResponse;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentConfirmRequest;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentConfirmResponse;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentErrorResponse;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

    private final RestClient tossRestClient;

    // 토스 멱등성 헤더. 같은 키로 재요청하면 토스가 첫 요청의 결과를 그대로 돌려준다.
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /***
     * 함수 기능: 토스페이먼츠에 결제 승인을 요청한다.
     * @param paymentKey 토스가 발급한 결제 키
     * @param orderId 서버가 생성한 주문 번호
     * @param totalAmount 승인할 금액 (DB에 저장된 값이어야 한다)
     * @return 승인된 결제 정보
     */
    public TossPaymentConfirmResponse confirm(String paymentKey, String orderId, Integer totalAmount) {

        try {
            return tossRestClient.post()
                    .uri("/confirm")
                    .body(TossPaymentConfirmRequest.of(paymentKey, orderId, totalAmount))
                    .retrieve()
                    .body(TossPaymentConfirmResponse.class);

        } catch (RestClientResponseException e) {
            logFailure(orderId, e);
            throw new PaymentException(PaymentErrorCode.PAYMENT_CONFIRM_FAILED);
        } catch (ResourceAccessException e) {
            log.error("[결제 승인 응답 없음] orderId={}, 승인 여부를 확인할 수 없어 결제 내역 대조가 필요합니다.", orderId, e);
            throw new PaymentException(PaymentErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }

    /***
     * 함수 기능: 토스가 내려준 실패 사유를 로그로 남긴다.
     * @param orderId 승인을 시도한 주문 번호
     * @param response 토스의 에러 응답
     */
    private void logFailure(String orderId, RestClientResponseException response) {

        try {
            TossPaymentErrorResponse error = response.getResponseBodyAs(TossPaymentErrorResponse.class);

            log.error("[결제 승인 실패] orderId={}, httpStatus={}, code={}, message={}",
                    orderId, response.getStatusCode(), error.code(), error.message());

        } catch (Exception e) {
            // 게이트웨이 장애 등으로 본문이 JSON이 아닐 수 있다. 이때도 승인 실패 자체는 기록해야 한다.
            log.error("[결제 승인 실패] orderId={}, 토스 에러 응답을 해석할 수 없습니다.", orderId, e);
        }
    }

    /***
     * 함수 기능: 토스페이먼츠에 결제 취소를 요청한다. 전체 취소와 부분 취소 모두 이 메서드를 쓴다.
     * @param paymentKey 토스가 발급한 결제 키
     * @param cancelAmount 취소할 금액 (좌석 단위로 계산한 값)
     * @param reason 취소 사유
     * @param idempotencyKey 같은 취소가 두 번 처리되지 않게 하는 멱등성 키
     * @return 취소가 반영된 결제 정보
     */
    public TossPaymentCancelResponse cancel(String paymentKey, Integer cancelAmount,
                                            String reason, String idempotencyKey) {

        try {
            return tossRestClient.post()
                    .uri("/{paymentKey}/cancel", paymentKey)
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(TossPaymentCancelRequest.of(reason, cancelAmount))
                    .retrieve()
                    .body(TossPaymentCancelResponse.class);

        } catch (RestClientResponseException e) {
            // 토스가 명시적으로 거절했다. 취소는 일어나지 않았으므로 되돌릴 것이 없다.
            throw new PaymentException(logCancelFailure(paymentKey, e));

        } catch (ResourceAccessException e) {
            // 응답이 오지 않았다. 취소가 됐는지 알 수 없으므로 취소 시도 기록을 지워서는 안 된다.
            log.error("[결제 취소 응답 없음] paymentKey={}, 취소 여부를 확인할 수 없어 결제 내역 대조가 필요합니다.",
                    paymentKey, e);
            throw new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        }
    }

    /***
     * 함수 기능: 토스가 내려준 취소 실패 사유를 로그로 남기고 대응하는 에러 코드를 돌려준다.
     * @param paymentKey 취소를 시도한 결제 키
     * @param response 토스의 에러 응답
     * @return 대응하는 결제 에러 코드
     */
    private PaymentErrorCode logCancelFailure(String paymentKey, RestClientResponseException response) {

        try {
            TossPaymentErrorResponse error = response.getResponseBodyAs(TossPaymentErrorResponse.class);

            log.error("[결제 취소 실패] paymentKey={}, httpStatus={}, code={}, message={}",
                    paymentKey, response.getStatusCode(), error.code(), error.message());

            return toCancelErrorCode(error.code());

        } catch (Exception e) {
            // 게이트웨이 장애 등으로 본문이 JSON이 아닐 수 있다. 이때도 취소 실패 자체는 기록해야 한다.
            log.error("[결제 취소 실패] paymentKey={}, 토스 에러 응답을 해석할 수 없습니다.", paymentKey, e);
            return PaymentErrorCode.PAYMENT_CANCEL_FAILED;
        }
    }

    /***
     * 함수 기능: 토스 에러 코드를 우리 에러 코드로 변환한다.
     * @param tossCode 토스 에러 응답의 code 값
     * @return 대응하는 결제 에러 코드. 모르는 코드는 일반 취소 실패로 묶는다.
     */
    private PaymentErrorCode toCancelErrorCode(String tossCode) {

        return switch (tossCode == null ? "" : tossCode) {
            case "ALREADY_CANCELED_PAYMENT" -> PaymentErrorCode.PAYMENT_ALREADY_CANCELED;
            case "EXCEED_CANCEL_AMOUNT", "INVALID_CANCEL_AMOUNT" -> PaymentErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED;
            default -> PaymentErrorCode.PAYMENT_CANCEL_FAILED;
        };
    }
}
