package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByBranchIdAndProductId(Long branchId, Long productId);

    // product를 조인하지 않는다. MySQL의 FOR UPDATE는 조인된 행까지 잠가서,
    // 모든 지점이 공유하는 product 행 때문에 지점이 달라도 같은 상품 구매가 줄을 서게 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.branch.id = :branchId AND s.product.id = :productId")
    Optional<Stock> findByBranchIdAndProductIdForUpdate(@Param("branchId") Long branchId,
                                                        @Param("productId") Long productId);

    @Query("""
            SELECT s FROM Stock s
            JOIN FETCH s.product
            WHERE s.branch.id = :branchId
            ORDER BY s.product.id
            """)
    List<Stock> findMenuByBranchId(@Param("branchId") Long branchId);
}
