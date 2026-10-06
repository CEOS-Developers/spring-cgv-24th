package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.FavoriteMovie;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteMovieRepository extends JpaRepository<FavoriteMovie, Long> {
    boolean existsByMovieIdAndMemberId(Long movieId, Long memberId);

    @EntityGraph(attributePaths = {"movie"})
    List<FavoriteMovie> findAllByMemberId(Long memberId);
}
