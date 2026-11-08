package com.ceos24.spring_cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

/***
 * 토스페이먼츠 결제 취소 API 응답. 취소된 결제 객체 전체가 내려오므로
 * 승인 응답과 마찬가지로 @JsonIgnoreProperties로 나머지를 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentCancelResponse(

        String paymentKey,
        String orderId,
        String status,              // CANCELED, PARTIAL_CANCELED
        Integer totalAmount,
        Integer balanceAmount,      // 취소 후 남은 금액. 우리 누적 취소 금액을 역산하는 기준값이다.
        List<CancelDetail> cancels
) {
    /***
     * 취소 거래 1건. 부분 취소를 여러 번 하면 과거 취소 거래까지 모두 내려온다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelDetail(

            String transactionKey,
            Integer cancelAmount,
            OffsetDateTime canceledAt,
            String cancelReason
    ) {}

    /***
     * 함수 기능: 이번에 처리된 취소 거래를 반환한다. cancels는 시간순이므로 마지막 원소다.
     * @return 가장 최근 취소 거래
     */
    public CancelDetail latestCancel() {
        return cancels.getLast();
    }
}
