package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByTheaterId(Long theaterId);
}
