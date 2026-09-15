package com.ceos24.spring_cgv.domain.store.repository;

import com.ceos24.spring_cgv.domain.store.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
}
