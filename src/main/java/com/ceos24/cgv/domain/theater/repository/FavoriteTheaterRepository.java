package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.domain.FavoriteTheater;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteTheaterRepository extends JpaRepository<FavoriteTheater, Long> {
}
