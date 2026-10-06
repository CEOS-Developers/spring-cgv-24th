package com.ceos24.springboot.theater.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.theater.domain.Screen;
import com.ceos24.springboot.theater.domain.Seat;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.dto.ScreenCreateRequest;
import com.ceos24.springboot.theater.repository.ScreenRepository;
import com.ceos24.springboot.theater.repository.SeatRepository;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenService {

    private final ScreenRepository screenRepository;
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;


    // 상영관 등록
    @Transactional
//  상영관과 좌석 생성이 한 세트로 이루어짐.
    public void createScreen(ScreenCreateRequest request) {

        // 1. 영화관 조회
        Theater theater = theaterRepository
                .findById(request.theaterId())
                .orElseThrow(() ->
                        new GlobalException(
                                ErrorCode.THEATER_NOT_FOUND
                        )
                );

        // 2. 상영관 생성
        Screen screen = Screen.create(
                theater,
                request.screenName(),
                request.screenType(),
                request.rowCount(),
                request.colCount()
        );

        // 3. 상영관 저장
        Screen savedScreen = screenRepository.save(screen);

        createSeats(savedScreen);
    }

//  좌석 생성
    private void createSeats(Screen screen) {

        List<Seat> seats = new ArrayList<>();

        for (int row = 0; row < screen.getRowCount(); row++) {

//          행을 숫자에서 문자로 변환
            String rowName =
                    String.valueOf((char) ('A' + row));

            for (int col = 1; col <= screen.getColCount(); col++) {

                Seat seat = Seat.create(
                        screen,
                        rowName,
                        col
                );

                seats.add(seat);
            }
        }

        seatRepository.saveAll(seats);
    }
}