package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;
import com.ceos24.cgv.domain.payment.service.target.PaymentTargetHandler;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchasePaymentHandler implements PaymentTargetHandler {
    private final PurchaseRepository purchases;
    private final PurchaseItemRepository purchaseItems;
    private final CinemaStockRepository stocks;

    @Override
    public PaymentTargetType type() { return PaymentTargetType.PURCHASE; }

    @Override
    public void lockTarget(Long targetId) {
        purchases.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_FOUND));
    }

    @Override
    public boolean isPayable(Payment payment) {
        return payment.getPurchase().getStatus() == PurchaseStatus.PENDING_PAYMENT;
    }

    @Override
    public void afterApproval(Payment payment) { payment.getPurchase().markPaid(); }

    @Override
    public void afterPartialCancellation(Payment payment) { payment.getPurchase().markPartiallyCanceled(); }

    @Override
    public void afterFullCancellation(Payment payment) {
        restoreStock(payment.getPurchase());
        payment.getPurchase().cancel();
    }

    @Override
    public void afterConfirmationRejected(Payment payment) { afterFullCancellation(payment); }

    @Override
    public void afterExpiration(Payment payment) {
        restoreStock(payment.getPurchase());
        payment.getPurchase().expire();
    }

    private void restoreStock(Purchase purchase) {
        List<PurchaseItem> items = purchaseItems.findAllByPurchaseIdOrderByIdAsc(purchase.getId());
        if (items.isEmpty()) return;
        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).sorted().toList();
        var stockByProduct = stocks.findAllByCinemaIdAndProductIdsForUpdate(purchase.getCinema().getId(), productIds)
                .stream().collect(Collectors.toMap(stock -> stock.getProduct().getId(), Function.identity()));
        if (stockByProduct.size() != productIds.size()) {
            throw new IllegalStateException("복구할 주문 재고가 존재하지 않습니다.");
        }
        for (PurchaseItem item : items) stockByProduct.get(item.getProduct().getId()).increase(item.getQuantity());
    }
}
