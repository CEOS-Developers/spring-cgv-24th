package com.cgvclone.cgv.domain.purchase;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByOrderId(String orderId);
}
