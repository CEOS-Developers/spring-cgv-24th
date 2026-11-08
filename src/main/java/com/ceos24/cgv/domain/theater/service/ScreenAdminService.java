package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreenAdminService {

    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public void createScreen(Long theaterId, ScreenCreateRequest request) {
        Theater theater = theaterRepository
                .findById(theaterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THEATER_NOT_FOUND));
        Screen screen = new Screen(theater, request.screenType(), request.name(), request.totalSeats());
        screenRepository.save(screen);

        List<Seat> seats = new ArrayList<>();
        for (long seatNumber = 1; seatNumber <= request.totalSeats(); seatNumber++) {
            seats.add(Seat.create(screen, seatNumber));
        }
        seatRepository.saveAll(seats);
    }
}
