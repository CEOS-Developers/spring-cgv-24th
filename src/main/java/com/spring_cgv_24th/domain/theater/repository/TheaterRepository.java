package com.spring_cgv_24th.domain.theater.repository;

import com.spring_cgv_24th.domain.theater.entity.Theater;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Theater t where t.id = :id")
    Optional<Theater> findByIdForUpdate(@Param("id") Long id);
}
