package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.screening.domain.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.dto.SeatInfo;
import com.ceos24.cgv.domain.theater.dto.response.GetSeatResponse;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SeatService {

    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public GetSeatResponse getSeats(Long screeningId) {
        List<SeatInfo> seatInfoList = seatRepository.findByScreeningId(screeningId).stream()
                .map(SeatInfo::from).toList();
        return new GetSeatResponse(screeningId, seatInfoList);
    }
}
