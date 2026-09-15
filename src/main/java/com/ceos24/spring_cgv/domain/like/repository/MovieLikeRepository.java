package com.ceos24.spring_cgv.domain.like.repository;

import com.ceos24.spring_cgv.domain.like.entity.MovieLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieLikeRepository extends JpaRepository<MovieLike, Long> {
}
