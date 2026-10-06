package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.Menu;
import com.ceos24.springboot.shop.domain.StoreInventory;
import com.ceos24.springboot.theater.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreInventoryRepository
        extends JpaRepository<StoreInventory, Long> {

    List<StoreInventory> findAllByTheater(Theater theater);

    Optional<StoreInventory> findByTheaterAndMenu(
            Theater theater,
            Menu menu
    );

    boolean existsByTheaterAndMenu(
            Theater theater,
            Menu menu
    );

    Optional<StoreInventory> findByInventoryIdAndTheater(
            Long inventoryId,
            Theater theater
    );
}