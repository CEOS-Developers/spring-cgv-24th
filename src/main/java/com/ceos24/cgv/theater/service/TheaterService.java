package com.ceos24.cgv.theater.service;

import com.ceos24.cgv.theater.dto.TheaterInfo;
import com.ceos24.cgv.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TheaterService {

    private final TheaterRepository theaterRepository;

    @Transactional(readOnly = true)
    public GetTheaterResponse getTheaters() {
        return new GetTheaterResponse(theaterRepository.findAll().stream()
                .map(TheaterInfo::from).toList());
    }
}
