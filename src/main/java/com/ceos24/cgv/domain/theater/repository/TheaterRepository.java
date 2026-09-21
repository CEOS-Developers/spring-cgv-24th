package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    List<Theater> findAllByOrderByNameAsc();
}