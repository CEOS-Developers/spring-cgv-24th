package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    List<PurchaseItem> findAllByPurchaseIdOrderByIdAsc(Long purchaseId);
}
