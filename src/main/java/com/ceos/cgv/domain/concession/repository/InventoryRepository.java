package com.ceos.cgv.domain.concession.repository;

import com.ceos.cgv.domain.concession.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByCinema_IdAndProduct_Id(Long cinemaId, Long productId);

    boolean existsByCinema_IdAndProduct_Id(Long cinemaId, Long productId);
}
