package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.dto.PurchaseHistoryRow;
import com.ceos24.cgv.domain.store.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 엔티티로 fetch join하면 branch.description(TEXT)이 구매 수 × 항목 수만큼 반복 전송된다.
    // 응답에 실리는 스칼라만 읽는다. 항목은 구매마다 1개 이상이라 INNER JOIN이어도 구매가 빠지지 않는다.
    @Query("""
            SELECT new com.ceos24.cgv.domain.store.dto.PurchaseHistoryRow(
                p.id, b.id, b.name, p.totalPrice, p.purchasedAt,
                pr.id, pr.name, item.quantity, item.unitPrice)
            FROM Purchase p
            JOIN p.branch b
            JOIN p.items item
            JOIN item.product pr
            WHERE p.user.id = :userId
            ORDER BY p.id DESC, item.id
            """)
    List<PurchaseHistoryRow> findHistoryRowsByUserId(@Param("userId") Long userId);
}
