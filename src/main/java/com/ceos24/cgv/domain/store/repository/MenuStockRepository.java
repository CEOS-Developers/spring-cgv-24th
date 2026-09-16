package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.domain.MenuStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MenuStockRepository extends JpaRepository<MenuStock, Long> {
    @Query("SELECT ms FROM MenuStock ms JOIN FETCH ms.menu WHERE ms.store.id = :storeId")
    List<MenuStock> findByStoreIdWithMenu(@Param("storeId") Long storeId);
}
