package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.purchase.Purchase;
import com.cgvclone.cgv.domain.purchase.PurchaseRepository;
import com.cgvclone.cgv.domain.purchase.PurchaseStatus;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseResponse;
import com.cgvclone.cgv.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentOrderService {
    private final PurchaseRepository purchases;
    private final StockRepository stocks;

    public void prepare(Long userId, PaymentConfirmRequest request) {
        if (request == null || request.orderId() == null || !request.orderId().matches("[A-Za-z0-9_=-]{6,64}")
                || request.paymentKey() == null || request.paymentKey().isBlank() || request.paymentKey().length() > 200
                || request.amount() == null || request.amount() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
        Purchase purchase = owned(userId, request.orderId());
        if (!purchase.getTotalPrice().equals(request.amount())) {
            throw new GlobalException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        purchase.beginConfirmation(request.paymentKey());
    }

    public PurchaseResponse complete(Long userId, PaymentConfirmRequest request, TossPayment payment) {
        TossPaymentClient.validate(payment, request);
        boolean paid = "DONE".equals(payment.status()) && payment.approvedAt() != null;
        if (!paid && !"WAITING_FOR_DEPOSIT".equals(payment.status())) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        Purchase purchase = owned(userId, request.orderId());
        if (!request.paymentKey().equals(purchase.getPaymentKey())
                || purchase.getStatus() != PurchaseStatus.CONFIRMING) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        if (paid) {
            purchase.markPaid(payment.approvedAt().toLocalDateTime());
        } else {
            purchase.waitForDeposit();
        }
        return PurchaseResponse.from(purchase);
    }

    public PurchaseResponse cancel(Long userId, String orderId) {
        Purchase purchase = owned(userId, orderId);
        // 승인 요청을 시작한 주문은 결과 확인 전 재고를 반환하지 않는다.
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        purchase.getPurchaseGoods()
                .forEach(i -> stocks.findByCinema_CinemaIdAndGoods_GoodsId(purchase.getCinema().getCinemaId(),
                                i.getGoods().getGoodsId()).orElseThrow(() -> new GlobalException(ErrorCode.STOCK_NOT_FOUND))
                        .increase(i.getQuantity()));
        purchase.cancel();
        return PurchaseResponse.from(purchase);
    }

    private Purchase owned(Long userId, String orderId) {
        Purchase purchase = purchases.findByOrderId(orderId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PURCHASE_NOT_FOUND));
        if (!purchase.getUser().getUserId().equals(userId)) {
            throw new GlobalException(ErrorCode.ACCESS_DENIED);
        }
        return purchase;
    }
}
