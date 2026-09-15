package com.ceos.cgv.domain.concession.repository;

import com.ceos.cgv.domain.concession.entity.FoodOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {
    @EntityGraph(attributePaths = "items")
    Optional<FoodOrder> findWithItemsById(Long orderId);
}
