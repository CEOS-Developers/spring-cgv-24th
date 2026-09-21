package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository.SeatCountProjection;
import com.ceos24.cgv.domain.screening.dto.ScreeningResponse;
import com.ceos24.cgv.domain.screening.dto.ScreeningSeatsResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreeningService {

    private final ScreeningRepository screeningRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final Clock clock;

    public List<ScreeningResponse> search(Long movieId, Long branchId, LocalDate date) {
        LocalDateTime startInclusive = date == null ? null : date.atStartOfDay();
        LocalDateTime endExclusive = date == null ? null : date.plusDays(1).atStartOfDay();

        List<Screening> screenings = screeningRepository.searchWithGraph(
                movieId, branchId, startInclusive, endExclusive);

        if (screenings.isEmpty()) {
            return List.of();
        }

        List<Long> ids = screenings.stream().map(Screening::getId).toList();
        Map<Long, Long> occupiedByScreening = reservationSeatRepository
                .countOccupiedByScreeningIds(ids, ReservationStatus.PENDING, LocalDateTime.now(clock)).stream()
                .collect(Collectors.toMap(
                        SeatCountProjection::getScreeningId,
                        SeatCountProjection::getReservedCount));

        return screenings.stream().map(s -> {
            int total = s.getTheater().getTheaterType().getTotalSeatCount();
            int occupied = occupiedByScreening.getOrDefault(s.getId(), 0L).intValue();
            return ScreeningResponse.from(s, total - occupied);
        }).toList();
    }

    public ScreeningSeatsResponse getSeats(Long screeningId) {
        Screening screening = screeningRepository.findByIdWithTheaterType(screeningId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));

        // 결제 전 선점도 남이 고를 수 없으므로 예매된 좌석과 똑같이 막힌 것으로 내려준다.
        List<String> labels = reservationSeatRepository
                .findOccupiedPositionsByScreeningId(screeningId, ReservationStatus.PENDING, LocalDateTime.now(clock))
                .stream()
                .map(p -> ReservationSeat.label(p.getRowNum(), p.getColNum()))
                .toList();

        return ScreeningSeatsResponse.from(screening, labels);
    }
}
