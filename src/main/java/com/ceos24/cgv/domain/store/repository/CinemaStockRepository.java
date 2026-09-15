package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.CinemaStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CinemaStockRepository extends JpaRepository<CinemaStock, Long> {

    boolean existsByCinemaIdAndProductId(Long cinemaId, Long productId);

    List<CinemaStock> findAllByCinemaIdOrderByProductIdAsc(Long cinemaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cs
            from CinemaStock cs
            join fetch cs.product
            where cs.cinema.id = :cinemaId
              and cs.product.id in :productIds
            order by cs.product.id
            """)
    List<CinemaStock> findAllByCinemaIdAndProductIdsForUpdate(
            @Param("cinemaId") Long cinemaId,
            @Param("productIds") List<Long> productIds
    );
}
