package com.ceos24.cgv.domain.snack.repository;

import com.ceos24.cgv.domain.snack.entity.SnackStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SnackStockRepository
        extends JpaRepository<SnackStock, Long> {

    // 여러 사용자가 동시에 같은 상품을 구매할 수 있음 ( 그래서 비관적 락 적용 . )
    // 비관적 락 : 첫번째 구매 트랜잭션이 재고 변경을 완료할때까지 다른 구매 요청이 해당 재고를 변경하지 못함 .
    // 재고 음수 방지함 .
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT stock
            FROM SnackStock stock
            JOIN FETCH stock.snackItem
            WHERE stock.theater.id = :theaterId
            AND stock.snackItem.id IN :snackItemIds
            """)
    List<SnackStock> findAllByTheaterIdAndSnackItemIdsForUpdate(
            @Param("theaterId") Long theaterId,
            @Param("snackItemIds") List<Long> snackItemIds
    );

    List<SnackStock> findAllForPurchase(
            @Param("theaterId") Long theaterId,
            @Param("snackItemIds") List<Long> snackItemIds
    );
}