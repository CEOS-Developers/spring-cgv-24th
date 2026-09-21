package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.OrderItem;
import com.ceos24.springboot.shop.domain.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    //    주문에 포함된 메뉴 저장 및 조회
    List<OrderItem> findAllByOrder(StoreOrder order);
}