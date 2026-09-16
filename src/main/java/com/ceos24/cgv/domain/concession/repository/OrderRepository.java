package com.ceos24.cgv.domain.concession.repository;

import com.ceos24.cgv.domain.concession.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.theater WHERE o.id = :id")
    Optional<Order> findByIdWithTheater(@Param("id") Long id);

    @Query("SELECT o FROM Order o JOIN FETCH o.theater WHERE o.user.id = :userId ORDER BY o.orderedAt DESC")
    List<Order> findByUserIdWithTheaterOrderByOrderedAtDesc(@Param("userId") Long userId);
}
