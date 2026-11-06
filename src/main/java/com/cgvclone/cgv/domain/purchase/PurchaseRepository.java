package com.cgvclone.cgv.domain.purchase;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByOrderId(String orderId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Purchase p
            set p.status = com.cgvclone.cgv.domain.purchase.PurchaseStatus.CONFIRMING,
                p.paymentKey = :paymentKey
            where p.orderId = :orderId and p.user.userId = :userId
              and p.totalPrice = :amount
              and p.status = com.cgvclone.cgv.domain.purchase.PurchaseStatus.PENDING
            """)
    int beginConfirmation(@Param("orderId") String orderId, @Param("userId") Long userId,
                          @Param("amount") Integer amount, @Param("paymentKey") String paymentKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Purchase p set p.status = :status, p.approvedAt = :approvedAt
            where p.orderId = :orderId and p.user.userId = :userId
              and p.paymentKey = :paymentKey and p.totalPrice = :amount
              and p.status = com.cgvclone.cgv.domain.purchase.PurchaseStatus.CONFIRMING
            """)
    int completeConfirmation(@Param("orderId") String orderId, @Param("userId") Long userId,
                             @Param("paymentKey") String paymentKey, @Param("amount") Integer amount,
                             @Param("status") PurchaseStatus status, @Param("approvedAt") LocalDateTime approvedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Purchase p set p.status = com.cgvclone.cgv.domain.purchase.PurchaseStatus.CANCELED
            where p.orderId = :orderId and p.user.userId = :userId
              and p.status = com.cgvclone.cgv.domain.purchase.PurchaseStatus.PENDING
            """)
    int cancelPending(@Param("orderId") String orderId, @Param("userId") Long userId);
}
