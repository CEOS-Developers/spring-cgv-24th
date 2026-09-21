package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieFavoriteRepository extends JpaRepository<MovieFavorite, Long> {
    boolean existsByUser_IdAndMovie_Id(Long userId, Long movieId);

    Optional<MovieFavorite> findByUser_IdAndMovie_Id(Long userId, Long movieId);

    List<MovieFavorite> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
