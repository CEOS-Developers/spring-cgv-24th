package com.ceos.cgv.domain.concession.repository;

import com.ceos.cgv.domain.concession.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByCinema_IdAndProduct_Id(Long cinemaId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventory
            from Inventory inventory
            where inventory.cinema.id = :cinemaId
              and inventory.product.id = :productId
            """)
    Optional<Inventory> findByCinema_IdAndProduct_IdForUpdate(
            @Param("cinemaId") Long cinemaId,
            @Param("productId") Long productId
    );

    boolean existsByCinema_IdAndProduct_Id(Long cinemaId, Long productId);
}
