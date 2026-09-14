package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByBranchIdAndProductId(Long branchId, Long productId);
}
