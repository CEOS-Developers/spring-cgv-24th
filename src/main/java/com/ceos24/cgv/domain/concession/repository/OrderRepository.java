package com.ceos24.cgv.domain.concession.repository;

import com.ceos24.cgv.domain.concession.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
