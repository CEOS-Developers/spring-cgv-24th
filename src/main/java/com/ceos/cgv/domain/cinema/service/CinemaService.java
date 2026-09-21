package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
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
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
    }
}
