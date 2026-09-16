package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.response.TheaterDetailResponse;
import com.ceos24.cgv.domain.theater.dto.response.TheaterListResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorCode;
import com.ceos24.cgv.domain.theater.exception.TheaterException;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterService {


    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;

    /**
     * 영화관 전체 조회
     */
    public List<TheaterListResponse> getTheaters() {
        return theaterRepository.findAllByOrderByNameAsc()
                .stream()
                .map(TheaterListResponse::from)
                .toList();
    }

    /**
     * 영화관 단건 조회
     */
    public TheaterDetailResponse getTheater(Long theaterId) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new TheaterException(
                                TheaterErrorCode.THEATER_NOT_FOUND
                        )
                );

        List<Screen> screens =
                screenRepository.findAllByTheaterIdWithScreenType(theaterId);

        return TheaterDetailResponse.from(theater, screens);
    }
}