package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.Screening;
import com.ceos24.cgv.dto.response.ScreeningResponse;
import com.ceos24.cgv.dto.response.ScreeningSeatsResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.ReservationSeatRepository;
import com.ceos24.cgv.repository.ReservationSeatRepository.SeatCountProjection;
import com.ceos24.cgv.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<ScreeningResponse> search(Long movieId, Long branchId, LocalDate date) {
        LocalDateTime startInclusive = date == null ? null : date.atStartOfDay();
        LocalDateTime endExclusive = date == null ? null : date.plusDays(1).atStartOfDay();

        List<Screening> screenings = screeningRepository.searchWithGraph(
                movieId, branchId, startInclusive, endExclusive);

        if (screenings.isEmpty()) {
            return List.of();
        }

        List<Long> ids = screenings.stream().map(Screening::getId).toList();
        Map<Long, Long> reservedByScreening = reservationSeatRepository
                .countGroupedByScreeningIds(ids).stream()
                .collect(Collectors.toMap(
                        SeatCountProjection::getScreeningId,
                        SeatCountProjection::getReservedCount));

        return screenings.stream().map(s -> {
            int total = s.getTheater().getTheaterType().getTotalSeatCount();
            int reserved = reservedByScreening.getOrDefault(s.getId(), 0L).intValue();
            return ScreeningResponse.from(s, total - reserved);
        }).toList();
    }

    public ScreeningSeatsResponse getSeats(Long screeningId) {
        Screening screening = screeningRepository.findByIdWithTheaterType(screeningId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));

        List<String> labels = reservationSeatRepository.findPositionsByScreeningId(screeningId)
                .stream()
                .map(p -> String.valueOf((char) ('A' + p.getRowNum() - 1)) + p.getColNum())
                .toList();

        return ScreeningSeatsResponse.from(screening, labels);
    }
}
