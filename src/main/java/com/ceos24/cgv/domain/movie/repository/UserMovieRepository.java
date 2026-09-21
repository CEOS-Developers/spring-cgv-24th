package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.UserMovie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMovieRepository
        extends JpaRepository<UserMovie, Long> {

    boolean existsByUser_IdAndMovie_Id(
            Long userId,
            Long movieId
    );

    Optional<UserMovie> findByUser_IdAndMovie_Id(
            Long userId,
            Long movieId
    );
}