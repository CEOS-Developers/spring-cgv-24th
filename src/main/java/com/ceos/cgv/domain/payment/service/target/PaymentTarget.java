package com.ceos.cgv.domain.payment.service.target;

import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.global.exception.ErrorCode;

import java.util.List;

public interface PaymentTarget {
    void beginApproval(PaymentOrder order, Long userId);

    void completeApproval(PaymentOrder order, Long userId);

    void releaseFailedApproval(PaymentOrder order, Long userId);

    // 작업 중인 결제가 있어도 잘못된 항목 요청은 먼저 거절한다. 이 단계는 주문 정보만 읽는다.
    List<Long> selectCancellationItems(PaymentOrder order, List<Long> requestedItemIds);

    // 결제를 잠근 트랜잭션에서 주문 대상을 잠그고 선택과 금액을 확인한다.
    long cancellationAmount(PaymentOrder order, Long userId, List<Long> itemIds);

    void completeCancellation(PaymentOrder order, Long userId, List<Long> itemIds);

    FinalizationFailure classifyFinalizationFailure(ErrorCode code);

    String approvalCompensationReason();

    enum FinalizationFailure {
        RETRY,
        COMPENSATE_APPROVAL,
        PENDING
    }
}
