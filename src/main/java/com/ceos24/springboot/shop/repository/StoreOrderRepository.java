package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoreOrderRepository
        extends JpaRepository<StoreOrder, Long> {
    Optional<StoreOrder> findByOrderIdAndUser_Id(Long orderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StoreOrder o where o.orderId = :orderId and o.user.id = :userId")
    Optional<StoreOrder> findMyOrderWithLock(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StoreOrder o where o.orderId = :orderId")
    Optional<StoreOrder> findByIdWithLock(@Param("orderId") Long orderId);

    @Query("""
            select o.orderId from StoreOrder o
            where o.status = :status and o.paymentProcessing = false and o.expiresAt <= :now
              and (:userId is null or o.user.id = :userId)
              and (:theaterId is null or o.theater.theaterId = :theaterId)
            order by o.orderId
            """)
    List<Long> findExpiredOrderIds(@Param("status") StoreOrderStatus status,
                                   @Param("now") LocalDateTime now,
                                   @Param("userId") Long userId,
                                   @Param("theaterId") Long theaterId,
                                   Pageable pageable);
}

