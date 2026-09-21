package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.TheaterLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TheaterLikeRepository extends JpaRepository<TheaterLike, Long> {
    Optional<TheaterLike> findByUserIdAndTheaterId(Long userId, Long theaterId);
}
