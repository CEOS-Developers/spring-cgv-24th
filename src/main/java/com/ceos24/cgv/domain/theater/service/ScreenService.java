package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.dto.response.ScreenCreateResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.ScreenTypeRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenService {

    private final TheaterRepository theaterRepository;
    private final ScreenTypeRepository screenTypeRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public ScreenCreateResponse create(Long theaterId, ScreenCreateRequest request) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));
        ScreenType screenType = screenTypeRepository.findById(request.screenTypeId())
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.SCREEN_TYPE_NOT_FOUND));
        Screen screen = screenRepository.save(Screen.create(theater, screenType, request.name()));
        List<Seat> seats = seatRepository.saveAll(screen.createSeats());
        return new ScreenCreateResponse(screen.getId(), seats.size());
    }
}
