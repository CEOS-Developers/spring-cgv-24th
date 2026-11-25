package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.entity.PaymentConfirm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentConfirmRepository extends JpaRepository<PaymentConfirm, Long> {
}
