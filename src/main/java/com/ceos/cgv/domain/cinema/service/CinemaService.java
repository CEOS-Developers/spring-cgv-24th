package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CinemaService {
    private final CinemaRepository cinemaRepository;

    public List<Cinema> findAll() {
        return cinemaRepository.findAll();
    }

    public Cinema findById(Long cinemaId){
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(()-> new IllegalArgumentException("영화관을 찾을 수 없습니다."));
    }
}
