package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.TheaterCreateRequest;
import com.ceos24.cgv.domain.theater.dto.request.TheaterUpdateRequest;
import com.ceos24.cgv.domain.theater.dto.response.TheaterResponse;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterService {

    private final TheaterRepository theaterRepository;

    @Transactional
    public Long create(TheaterCreateRequest request) {
        Theater theater = Theater.create(request.name(), request.region(), request.address(), request.description(), request.theaterImageUrl());
        Theater saved = theaterRepository.save(theater);
        return saved.getId();
    }

    public List<TheaterResponse> findAll() {
        return theaterRepository.findAll().stream()
                .map(TheaterResponse::from)
                .toList();
    }

    public TheaterResponse findById(Long id) {
        Theater theater = getTheaterOrThrow(id);
        return TheaterResponse.from(theater);
    }

    @Transactional
    public void update(Long id, TheaterUpdateRequest request) {
        Theater theater = getTheaterOrThrow(id);
        theater.update(
                request.name(), request.region(), request.address(),
                request.description(), request.theaterImageUrl()
        );
    }

    @Transactional
    public void delete(Long id) {
        Theater theater = getTheaterOrThrow(id);
        theaterRepository.delete(theater);
    }

    private Theater getTheaterOrThrow(Long id) {
        return theaterRepository.findById(id)
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));
    }
}
