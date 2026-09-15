package com.ceos24.springboot.favorite.repository;

import com.ceos24.springboot.favorite.domain.MovieLikes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieLikesRepository
        extends JpaRepository<MovieLikes, Long> {
}