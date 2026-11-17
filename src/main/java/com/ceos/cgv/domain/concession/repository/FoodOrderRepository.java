package com.ceos.cgv.domain.concession.repository;

import com.ceos.cgv.domain.concession.entity.FoodOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<FoodOrder> findWithItemsById(Long orderId);

    @EntityGraph(attributePaths = "items")
    Optional<FoodOrder> findByUser_IdAndCheckoutRequestKey(Long userId, String checkoutRequestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from FoodOrder o where o.id = :orderId")
    Optional<FoodOrder> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select o.id as orderId, o.holdExpiresAt as expiresAt
            from FoodOrder o
            where o.paymentStatus = com.ceos.cgv.domain.concession.enums.FoodOrderPaymentStatus.HELD
              and o.holdExpiresAt <= :now
              and (:afterExpiresAt is null or o.holdExpiresAt > :afterExpiresAt
                   or (o.holdExpiresAt = :afterExpiresAt and o.id > :afterId))
            order by o.holdExpiresAt, o.id
            """)
    List<ExpiredCheckout> findExpiredCheckouts(
            @Param("now") Instant now,
            @Param("afterExpiresAt") Instant afterExpiresAt,
            @Param("afterId") Long afterId,
            Pageable pageable);

    interface ExpiredCheckout {
        Long getOrderId();

        Instant getExpiresAt();
    }
}
