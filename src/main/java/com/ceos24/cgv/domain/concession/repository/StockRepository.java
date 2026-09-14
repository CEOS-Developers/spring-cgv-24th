package com.ceos24.cgv.domain.concession.repository;

import com.ceos24.cgv.domain.concession.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByTheaterIdAndItemId(Long theaterId, Long itemId);
}
