package com.ceos24.springboot.movie.repository;

import com.ceos24.springboot.movie.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
// 영화 조회 메서드 추가해야함
}