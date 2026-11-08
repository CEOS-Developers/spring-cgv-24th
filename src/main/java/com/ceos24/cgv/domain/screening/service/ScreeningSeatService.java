package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.reservation.repository.SeatReservationRepository;
import com.ceos24.cgv.domain.screening.dto.ScreeningSeatInfo;
import com.ceos24.cgv.domain.screening.dto.SeatStatus;
import com.ceos24.cgv.domain.screening.dto.response.GetScreeningSeatResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreeningSeatService {

    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;

    @Transactional(readOnly = true)
    public GetScreeningSeatResponse getSeats(Long screeningId) {
        Screening screening = screeningRepository
                .findById(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));

        List<Seat> seats = seatRepository.findByScreenIdOrderBySeatNumberAsc(
                screening.getScreen().getId());

        LocalDateTime now = LocalDateTime.now();
        Map<Long, SeatStatus> seatStatusMap =
                seatReservationRepository.findAllWithReservationByScreeningId(screeningId).stream()
                        .collect(Collectors.toMap(
                                sr -> sr.getSeat().getId(), sr -> sr.toSeatStatus(now), (s1, s2) -> s1));

        List<ScreeningSeatInfo> seatInfoList = seats.stream()
                .map(seat -> ScreeningSeatInfo.of(seat, seatStatusMap.getOrDefault(seat.getId(), SeatStatus.AVAILABLE)))
                .toList();

        return new GetScreeningSeatResponse(screeningId, seatInfoList);
    }
}
