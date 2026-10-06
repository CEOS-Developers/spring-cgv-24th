package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.MenuStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuStockRepository extends JpaRepository<MenuStock, Long> {

    @Query("SELECT ms FROM MenuStock ms JOIN FETCH ms.menu WHERE ms.store.id = :storeId")
    List<MenuStock> findByStoreIdWithMenu(@Param("storeId") Long storeId);

    List<MenuStock> findAllByStoreIdAndMenuIdIn(Long storeId, List<Long> menuIds);

    Optional<MenuStock> findByStoreIdAndMenuId(Long storeId, Long menuId);
}
