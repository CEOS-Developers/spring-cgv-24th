package com.ceos24.spring_cgv.domain.store.repository;

import com.ceos24.spring_cgv.domain.store.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
}
