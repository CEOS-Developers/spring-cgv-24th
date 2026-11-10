package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.MenuStock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuStockRepository extends JpaRepository<MenuStock, Long> {

    @Query("SELECT ms FROM MenuStock ms JOIN FETCH ms.menu WHERE ms.store.id = :storeId")
    List<MenuStock> findByStoreIdWithMenu(@Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MenuStock> findAllByStoreIdAndMenuIdIn(Long storeId, List<Long> menuIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT ms FROM MenuStock ms WHERE ms.store.id = :storeId AND ms.menu.id IN :menuIds ORDER BY ms.menu.id ASC")
    List<MenuStock> findAllByStoreIdAndMenuIdInOrderByMenuIdAsc(
            @Param("storeId") Long storeId, @Param("menuIds") List<Long> menuIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MenuStock> findByStoreIdAndMenuId(Long storeId, Long menuId);
}
