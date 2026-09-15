package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {
//    주문에 포함된 메뉴 저장 및 조회
}
