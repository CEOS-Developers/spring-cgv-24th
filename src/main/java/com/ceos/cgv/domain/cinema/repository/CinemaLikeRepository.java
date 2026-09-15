package com.ceos.cgv.domain.cinema.repository;

import com.ceos.cgv.domain.cinema.entity.CinemaLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CinemaLikeRepository extends JpaRepository<CinemaLike, Long> {
    boolean existsByUser_IdAndCinema_Id(Long userId, Long cinemaId);

    Optional<CinemaLike> findByUser_IdAndCinema_Id(Long userId, Long cinemaId);
}
