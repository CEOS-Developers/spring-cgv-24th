package com.spring_cgv_24th.domain.store.repository;

import com.spring_cgv_24th.domain.store.entity.TheaterStock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TheaterStockRepository extends JpaRepository<TheaterStock, Long> {

    @EntityGraph(attributePaths = "product")
    List<TheaterStock> findAllByTheater_IdOrderByProduct_IdAsc(Long theaterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TheaterStock s where s.theater.id = :theaterId and s.product.id = :productId")
    Optional<TheaterStock> findByTheaterIdAndProductIdForUpdate(
            @Param("theaterId") Long theaterId, @Param("productId") Long productId);
}
