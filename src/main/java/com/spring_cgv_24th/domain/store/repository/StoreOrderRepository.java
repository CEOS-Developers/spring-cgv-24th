package com.spring_cgv_24th.domain.store.repository;

import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
}
