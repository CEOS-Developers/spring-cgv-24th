package com.ceos24.cgv.domain.snack.repository;

import com.ceos24.cgv.domain.snack.entity.SnackOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SnackOrderRepository
        extends JpaRepository<SnackOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SnackOrder o where o.id = :id")
    Optional<SnackOrder> findByIdForUpdate(@Param("id") Long id);
}
