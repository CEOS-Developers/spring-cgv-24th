package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {
    // 한 시네마에 중복된 이름의 상영관이 있는지 체크
    boolean existsByCinemaIdAndName(Long cinemaId, String name);

    // 한 시네마의 모든 상영관 조회
    List<Auditorium> findAllByCinemaIdOrderByIdAsc(Long cinemaId);
}
