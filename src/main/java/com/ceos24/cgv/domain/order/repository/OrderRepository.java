package com.ceos24.cgv.domain.order.repository;

import com.ceos24.cgv.domain.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
