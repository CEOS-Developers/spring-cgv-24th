package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.dto.request.CinemaCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.CinemaResponse;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    @Transactional
    public Long createCinema(CinemaCreateRequest request) {
        Cinema cinema = Cinema.create(request.name(), request.address(), request.region());
        return cinemaRepository.save(cinema).getId();
    }

    public List<CinemaResponse> getCinemas() {
        return cinemaRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(CinemaResponse::from)
                .toList();
    }

    public CinemaResponse getCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
        return CinemaResponse.from(cinema);
    }

    @Transactional
    public void deleteCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
        cinemaRepository.delete(cinema);
    }
}
