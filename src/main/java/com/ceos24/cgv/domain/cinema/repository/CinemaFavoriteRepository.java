package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.CinemaFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CinemaFavoriteRepository extends JpaRepository<CinemaFavorite, Long> {

    boolean existsByUserEntity_IdAndCinema_Id(Long userId, Long cinemaId);

    Optional<CinemaFavorite> findByUserEntity_IdAndCinema_Id(Long userId, Long cinemaId);

    List<CinemaFavorite> findAllByUserEntity_IdOrderByCreatedAtDesc(Long userId);
}
