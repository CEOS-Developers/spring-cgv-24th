package com.spring_cgv_24th.domain.store.repository;

import com.spring_cgv_24th.domain.store.entity.StoreOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderItemRepository extends JpaRepository<StoreOrderItem, Long> {

    List<StoreOrderItem> findAllByOrder_IdOrderByProduct_IdAsc(Long orderId);
}
