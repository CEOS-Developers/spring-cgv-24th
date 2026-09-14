package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.MovieLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieLikeRepository extends JpaRepository<MovieLike, Long> {
    boolean existsByUserIdAndMovieId(Long userId, Long movieId);
}
