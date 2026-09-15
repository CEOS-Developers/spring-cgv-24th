package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository
        extends JpaRepository<StoreOrder, Long> {
    //    주문 생성 및 주문내역 조회
}