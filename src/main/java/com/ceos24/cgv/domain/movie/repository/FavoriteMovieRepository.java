package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.domain.FavoriteMovie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteMovieRepository extends JpaRepository<FavoriteMovie, Long> {}
