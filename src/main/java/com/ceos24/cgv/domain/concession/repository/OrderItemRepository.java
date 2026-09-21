package com.ceos24.cgv.domain.concession.repository;

import com.ceos24.cgv.domain.concession.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.item WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderIdWithItem(@Param("orderId") Long orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.item WHERE oi.order.id IN :orderIds")
    List<OrderItem> findByOrderIdInWithItem(@Param("orderIds") List<Long> orderIds);
}
