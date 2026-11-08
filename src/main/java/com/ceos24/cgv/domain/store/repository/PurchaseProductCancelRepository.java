package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.PurchaseProductCancel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseProductCancelRepository extends JpaRepository<PurchaseProductCancel, Long> {

    // 확정할 때 상품 라인을 바로 바꾸므로 함께 읽는다. 라인 수만큼 지연 로딩이 나가지 않게 한다.
    @Query("""
            SELECT d FROM PurchaseProductCancel d
            JOIN FETCH d.purchaseProduct
            WHERE d.paymentCancel.id = :paymentCancelId
            """)
    List<PurchaseProductCancel> findByPaymentCancelId(@Param("paymentCancelId") Long paymentCancelId);
}
