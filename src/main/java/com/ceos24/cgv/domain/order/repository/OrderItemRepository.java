package com.ceos24.cgv.domain.order.repository;

import com.ceos24.cgv.domain.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
