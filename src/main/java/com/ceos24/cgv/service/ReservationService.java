package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.Reservation;
import com.ceos24.cgv.domain.Screening;
import com.ceos24.cgv.domain.TheaterType;
import com.ceos24.cgv.domain.User;
import com.ceos24.cgv.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.dto.response.ReservationResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.ReservationRepository;
import com.ceos24.cgv.repository.ReservationSeatRepository;
import com.ceos24.cgv.repository.ScreeningRepository;
import com.ceos24.cgv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final UserRepository userRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    @Transactional
    public ReservationResponse create(ReservationCreateRequest req) {
        // 1. 회차 존재 (theater + theaterType 함께 로딩)
        Screening screening = screeningRepository.findByIdWithTheaterType(req.screeningId())
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));

        // 2. 사용자 존재
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 좌석 범위 검증
        TheaterType type = screening.getTheater().getTheaterType();
        for (ReservationCreateRequest.SeatRequest s : req.seats()) {
            if (!type.isValidSeat(s.rowNum(), s.colNum())) {
                throw new CustomException(ErrorCode.SEAT_OUT_OF_RANGE);
            }
        }

        // 4. 요청 내 중복 검증
        long distinctCount = req.seats().stream()
                .map(s -> List.of(s.rowNum(), s.colNum()))
                .distinct()
                .count();
        if (distinctCount != req.seats().size()) {
            throw new CustomException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        }

        // 5. 이미 예매된 좌석 pre-check
        Set<String> taken = reservationSeatRepository.findByScreeningId(screening.getId()).stream()
                .map(rs -> rs.getRowNum() + ":" + rs.getColNum())
                .collect(Collectors.toSet());
        for (ReservationCreateRequest.SeatRequest s : req.seats()) {
            if (taken.contains(s.rowNum() + ":" + s.colNum())) {
                throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
            }
        }

        // 6. 예매 생성
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        int price = screening.getPrice();
        req.seats().forEach(s -> reservation.addSeat(s.rowNum(), s.colNum(), price));

        // 7. 저장 + 경쟁 상태 안전망 (동시 요청으로 유니크 제약 위반 시 포착)
        try {
            return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
        }
    }

    public ReservationResponse getById(Long id) {
        Reservation r = reservationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        return ReservationResponse.from(r);
    }

    @Transactional
    public void cancel(Long id) {
        Reservation r = reservationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        r.cancel();
    }
}
