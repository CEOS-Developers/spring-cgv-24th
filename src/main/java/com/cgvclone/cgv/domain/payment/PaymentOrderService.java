package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.purchase.Purchase;
import com.cgvclone.cgv.domain.purchase.PurchaseRepository;
import com.cgvclone.cgv.domain.purchase.PurchaseStatus;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseResponse;
import com.cgvclone.cgv.domain.stock.StockRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentOrderService {
    private final PurchaseRepository purchases;
    private final StockRepository stocks;

    public Optional<PurchaseResponse> prepare(Long userId, PaymentConfirmRequest request) {
        if (request == null || request.orderId() == null || !request.orderId().matches("[A-Za-z0-9_=-]{6,64}")
                || request.paymentKey() == null || request.paymentKey().isBlank() || request.paymentKey().length() > 200
                || request.amount() == null || request.amount() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
        Purchase purchase = ownedForUpdate(userId, request.orderId());
        if (!purchase.getTotalPrice().equals(request.amount())) {
            throw new GlobalException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            requireSamePayment(purchase, request);
            if (confirmationCompleted(purchase)) {
                return Optional.of(PurchaseResponse.from(purchase));
            }
            if (purchase.getStatus() == PurchaseStatus.CONFIRMING) {
                return Optional.empty();
            }
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        if (purchases.beginConfirmation(request.orderId(), userId, request.amount(), request.paymentKey()) != 1) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        return Optional.empty();
    }

    public PurchaseResponse complete(Long userId, PaymentConfirmRequest request, TossPayment payment) {
        TossPaymentClient.validate(payment, request);
        boolean paid = "DONE".equals(payment.status()) && payment.approvedAt() != null;
        if (!paid && !"WAITING_FOR_DEPOSIT".equals(payment.status())) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        Purchase purchase = ownedForUpdate(userId, request.orderId());
        requireSamePayment(purchase, request);
        if (confirmationCompleted(purchase)) {
            return PurchaseResponse.from(purchase);
        }
        PurchaseStatus status = paid ? PurchaseStatus.PAID : PurchaseStatus.WAITING_FOR_DEPOSIT;
        if (purchases.completeConfirmation(request.orderId(), userId, request.paymentKey(), request.amount(), status,
                paid ? payment.approvedAt().toLocalDateTime() : null) != 1) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        return PurchaseResponse.from(owned(userId, request.orderId()));
    }

    public PurchaseResponse cancel(Long userId, String orderId) {
        owned(userId, orderId);
        if (purchases.cancelPending(orderId, userId) != 1) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        PurchaseResponse response = PurchaseResponse.from(owned(userId, orderId));
        response.items().stream()
                .sorted(Comparator.comparing(PurchaseResponse.Item::goodsId))
                .forEach(item -> {
                    if (stocks.restoreQuantity(response.cinemaId(), item.goodsId(), item.quantity()) != 1) {
                        throw new GlobalException(ErrorCode.STOCK_RESTORE_FAILED);
                    }
                });
        return response;
    }

    private Purchase owned(Long userId, String orderId) {
        Purchase purchase = purchases.findByOrderId(orderId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PURCHASE_NOT_FOUND));
        if (!purchase.getUser().getUserId().equals(userId)) {
            throw new GlobalException(ErrorCode.ACCESS_DENIED);
        }
        return purchase;
    }

    private Purchase ownedForUpdate(Long userId, String orderId) {
        Purchase purchase = purchases.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PURCHASE_NOT_FOUND));
        if (!purchase.getUser().getUserId().equals(userId)) {
            throw new GlobalException(ErrorCode.ACCESS_DENIED);
        }
        return purchase;
    }

    private void requireSamePayment(Purchase purchase, PaymentConfirmRequest request) {
        if (!request.paymentKey().equals(purchase.getPaymentKey())
                || !request.amount().equals(purchase.getTotalPrice())) {
            throw new GlobalException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
    }

    private boolean confirmationCompleted(Purchase purchase) {
        return purchase.getStatus() == PurchaseStatus.PAID
                || purchase.getStatus() == PurchaseStatus.WAITING_FOR_DEPOSIT;
    }
}
