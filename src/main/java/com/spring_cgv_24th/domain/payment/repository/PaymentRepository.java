package com.spring_cgv_24th.domain.payment.repository;

import com.spring_cgv_24th.domain.payment.entity.Payment;
import com.spring_cgv_24th.domain.payment.enums.PaymentStatus;
import com.spring_cgv_24th.domain.payment.enums.PaymentTargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTargetTypeAndTargetId(PaymentTargetType targetType, Long targetId);
    boolean existsByPaymentKey(String paymentKey);

    @Query("""
            select p from Payment p
            where p.status in :cancelStatuses or (p.status in :statuses and (
                (p.targetType = com.spring_cgv_24th.domain.payment.enums.PaymentTargetType.STORE_ORDER
                 and exists (select o.id from StoreOrder o where o.id = p.targetId and o.expiresAt <= :now))
                or
                (p.targetType = com.spring_cgv_24th.domain.payment.enums.PaymentTargetType.RESERVATION
                 and exists (select r.id from Reservation r where r.id = p.targetId and r.expiresAt <= :now))
            )) order by p.id
            """)
    List<Payment> findUnresolvedPayments(@Param("statuses") List<PaymentStatus> statuses,
                                         @Param("cancelStatuses") List<PaymentStatus> cancelStatuses,
                                         @Param("now") LocalDateTime now);
}
