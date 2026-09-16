package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
}
