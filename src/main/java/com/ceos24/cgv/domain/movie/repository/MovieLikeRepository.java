package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MovieLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieLikeRepository extends JpaRepository<MovieLike, Long> {
    Optional<MovieLike> findByUserIdAndMovieId(Long userId, Long movieId);
}
