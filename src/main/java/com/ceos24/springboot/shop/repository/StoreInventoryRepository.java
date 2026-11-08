package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.Menu;
import com.ceos24.springboot.shop.domain.StoreInventory;
import com.ceos24.springboot.theater.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreInventoryRepository
        extends JpaRepository<StoreInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StoreInventory i where i.theater.theaterId = :theaterId and i.menu.menuId = :menuId")
    Optional<StoreInventory> findWithLock(@Param("theaterId") Long theaterId, @Param("menuId") Long menuId);

    List<StoreInventory> findAllByTheater(Theater theater);

    Optional<StoreInventory> findByTheaterAndMenu(
            Theater theater,
            Menu menu
    );

    boolean existsByTheaterAndMenu(
            Theater theater,
            Menu menu
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StoreInventory> findByInventoryIdAndTheater(
            Long inventoryId,
            Theater theater
    );
}
