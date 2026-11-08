package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.dto.PurchaseHistoryRow;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    // 같은 주문의 승인 요청이 겹치지 않도록 구매 행을 잠근다. 소유자 조건을 걸어 남의 주문은 없는 주문과 같다.
    // 조인하지 않는다. FOR UPDATE는 조인한 행까지 잠가, 지점을 붙이면 같은 지점의 결제가 전부 줄을 선다.
    // 이 조회가 트랜잭션의 첫 쿼리여야 한다(ReservationRepository.findOwnedForUpdate와 같은 이유).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id AND p.user.id = :userId")
    Optional<Purchase> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 관리자 환불용. 매장이 환불하므로 소유자 조건이 없다. 조인하지 않고 트랜잭션의 첫 쿼리여야 하는 이유는 위와 같다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") Long id);

    // 승인 확정 응답을 한 쿼리로 만든다. 구매 한 건이라 branch.description이 반복되는 양은 항목 수만큼이다.
    @Query("""
            SELECT p FROM Purchase p
            JOIN FETCH p.branch
            JOIN FETCH p.items item
            JOIN FETCH item.product
            WHERE p.id = :id
            """)
    Optional<Purchase> findWithDetails(@Param("id") Long id);

    // 엔티티로 fetch join하면 branch.description(TEXT)이 구매 수 × 항목 수만큼 반복 전송된다.
    // 응답에 실리는 스칼라만 읽는다. 항목은 구매마다 1개 이상이라 INNER JOIN이어도 구매가 빠지지 않는다.
    // 결제가 끝난 구매만 보인다. 결제창을 닫고 떠난 주문이나 거절된 주문은 산 것이 아니다.
    // 모두 환불된 구매는 보인다. 산 적이 있고, 회원이 환불됐는지 확인할 곳이 여기다.
    @Query("""
            SELECT new com.ceos24.cgv.domain.store.dto.PurchaseHistoryRow(
                p.id, b.id, b.name, p.status, p.totalPrice, p.purchasedAt, p.cancelledAt,
                pr.id, pr.name, item.quantity, item.canceledQuantity, item.unitPrice)
            FROM Purchase p
            JOIN p.branch b
            JOIN p.items item
            JOIN item.product pr
            WHERE p.user.id = :userId AND p.status IN :statuses
            ORDER BY p.id DESC, item.id
            """)
    List<PurchaseHistoryRow> findHistoryRowsByUserId(@Param("userId") Long userId,
                                                     @Param("statuses") Collection<PurchaseStatus> statuses);
}
