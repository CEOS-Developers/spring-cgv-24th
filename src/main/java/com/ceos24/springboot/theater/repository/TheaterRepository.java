package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
//    영화관 조회 메서드 추가해야함
}