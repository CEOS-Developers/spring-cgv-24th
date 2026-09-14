package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {}
