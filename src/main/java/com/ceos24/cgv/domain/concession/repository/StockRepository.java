package com.ceos24.cgv.domain.concession.repository;

import com.ceos24.cgv.domain.concession.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByTheaterIdAndItemId(Long theaterId, Long itemId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.item WHERE s.theater.id = :theaterId ORDER BY s.item.name ASC")
    List<Stock> findByTheaterIdWithItemOrderByItemNameAsc(@Param("theaterId") Long theaterId);

    @Modifying
    @Query("UPDATE Stock s SET s.quantity = s.quantity - :quantity " +
            "WHERE s.theater.id = :theaterId AND s.item.id = :itemId AND s.quantity >= :quantity")
    int decreaseStock(@Param("theaterId") Long theaterId, @Param("itemId") Long itemId, @Param("quantity") Integer quantity);
}
