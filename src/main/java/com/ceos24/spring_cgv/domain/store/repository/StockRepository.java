package com.ceos24.spring_cgv.domain.store.repository;

import com.ceos24.spring_cgv.domain.store.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
