package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findAllByUserIdOrderByPurchasedAtDesc(Long userId);
}
