package com.ceos24.cgv.domain.snack.repository;

import com.ceos24.cgv.domain.snack.entity.SnackOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnackOrderRepository
        extends JpaRepository<SnackOrder, Long> {
}