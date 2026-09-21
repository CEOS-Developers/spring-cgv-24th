package com.ceos24.springboot.theater.service;

import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.dto.TheaterCreateRequest;
import com.ceos24.springboot.theater.dto.TheaterResponse;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterService {

    private final TheaterRepository theaterRepository;

    // 영화관 등록
    @Transactional
    public TheaterResponse createTheater(TheaterCreateRequest request) {

        Theater savedTheater =
                theaterRepository.save(request.toEntity());

        return TheaterResponse.from(savedTheater);
    }

    // 영화관 전체 조회
    public List<TheaterResponse> getTheaters() {

        return theaterRepository.findAll()
                .stream()
                .map(TheaterResponse::from)
                .toList();
    }

    // 지역별 영화관 조회
    public List<TheaterResponse> getTheatersByRegion(Region region) {

        return theaterRepository.findByRegion(region)
                .stream()
                .map(TheaterResponse::from)
                .toList();
    }

    // 영화관 하나 조회
    public TheaterResponse getTheater(Long theaterId) {

        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다. theaterId=" + theaterId
                        )
                );

        return TheaterResponse.from(theater);
    }
}