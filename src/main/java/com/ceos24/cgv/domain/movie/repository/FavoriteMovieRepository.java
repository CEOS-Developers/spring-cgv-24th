package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.FavoriteMovie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteMovieRepository extends JpaRepository<FavoriteMovie, Long> {
    boolean existsByMovieIdAndMemberId(Long movieId, Long memberId);

    @EntityGraph(attributePaths = {"movie"})
    List<FavoriteMovie> findAllByMemberId(Long memberId);
}
