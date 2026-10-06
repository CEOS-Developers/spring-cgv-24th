package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.theater.dto.SeatInfo;
import com.ceos24.cgv.domain.theater.dto.response.GetSeatResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SeatService {

    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public GetSeatResponse getSeats(Long screeningId) {
        List<SeatInfo> seatInfoList = seatRepository.findByScreeningId(screeningId).stream()
                .map(SeatInfo::from)
                .toList();
        return new GetSeatResponse(screeningId, seatInfoList);
    }

    @Transactional
    public void createSeats(Screening screening) {
        Screen screen = screening.getScreen();
        List<Seat> newSeats = new ArrayList<>();
        for (int i = 0; i < screen.getTotalSeats(); i++) {
            Seat seat = new Seat(screening, (long) i + 1, false);
            newSeats.add(seat);
        }
        seatRepository.saveAll(newSeats);
    }
}
