package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancellationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentCancellationSeatRepository
        extends JpaRepository<PaymentCancellationSeat, Long> {

    List<PaymentCancellationSeat> findAllByCancellation_Id(Long cancellationId);
}