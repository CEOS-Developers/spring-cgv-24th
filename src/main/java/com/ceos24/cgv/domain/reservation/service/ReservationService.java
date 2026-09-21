package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.reservation.dto.PaymentRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
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
    private final Clock clock;

    @Transactional
    public ReservationResponse create(ReservationCreateRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 회차 존재 (응답이 읽는 영화·상영관·지점까지 함께 로딩)
        Screening screening = screeningRepository.findByIdWithDetails(req.screeningId())
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));

        // 2. 사용자 존재
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 3. 만료된 선점 정리. 유니크 인덱스는 만료 시각을 모르므로,
        //    행을 놓아주지 않으면 시간이 지난 좌석도 다시 선택할 수 없다.
        releaseExpiredHolds(screening.getId(), now);

        // 4. 좌석 범위 검증
        TheaterType type = screening.getTheater().getTheaterType();
        for (ReservationCreateRequest.SeatRequest s : req.seats()) {
            if (!type.isValidSeat(s.rowNum(), s.colNum())) {
                throw new CustomException(ErrorCode.SEAT_OUT_OF_RANGE);
            }
        }

        // 5. 요청 내 중복 검증
        long distinctCount = req.seats().stream()
                .map(s -> List.of(s.rowNum(), s.colNum()))
                .distinct()
                .count();
        if (distinctCount != req.seats().size()) {
            throw new CustomException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        }

        // 6. 이미 점유된 좌석 pre-check. 경쟁이 없는 경우에 친절한 응답을 주기 위한 것이고,
        //    검사와 INSERT 사이의 틈은 7번의 유니크 제약이 막는다.
        Set<String> taken = reservationSeatRepository
                .findOccupiedPositionsByScreeningId(screening.getId(), ReservationStatus.PENDING, now).stream()
                .map(p -> p.getRowNum() + ":" + p.getColNum())
                .collect(Collectors.toSet());
        for (ReservationCreateRequest.SeatRequest s : req.seats()) {
            if (taken.contains(s.rowNum() + ":" + s.colNum())) {
                throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
            }
        }

        // 7. 선점 생성 + 경쟁 상태 안전망 (동시 요청으로 유니크 제약 위반 시 포착)
        Reservation reservation = Reservation.builder()
                .user(user).screening(screening).now(now).build();
        int basePrice = screening.getPrice();
        req.seats().forEach(s -> reservation.addSeat(s.rowNum(), s.colNum(), s.audienceType(), basePrice));

        try {
            return ReservationResponse.from(reservationRepository.saveAndFlush(reservation), now);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
        }
    }

    // 결제 실패는 예외로 알리지만 좌석 해제는 남아야 하므로 롤백 대상에서 뺀다.
    @Transactional(noRollbackFor = CustomException.class)
    public ReservationResponse pay(Long id, PaymentRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = findWithDetails(id);

        if (req.result() == PaymentRequest.PaymentResult.FAILURE) {
            reservation.cancel(now);
            throw new CustomException(ErrorCode.PAYMENT_FAILED);
        }

        reservation.confirm(now);
        return ReservationResponse.from(reservation, now);
    }

    public ReservationResponse getById(Long id) {
        return ReservationResponse.from(findWithDetails(id), LocalDateTime.now(clock));
    }

    @Transactional
    public void cancel(Long id) {
        findWithSeats(id).cancel(LocalDateTime.now(clock));
    }

    private Reservation findWithDetails(Long id) {
        return reservationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation findWithSeats(Long id) {
        return reservationRepository.findByIdWithSeats(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    // 해제를 INSERT보다 먼저 DB에 반영해야 한다. 한 번에 flush하면 Hibernate가
    // INSERT를 UPDATE보다 앞서 내보내 같은 좌석에서 유니크 충돌이 난다.
    private void releaseExpiredHolds(Long screeningId, LocalDateTime now) {
        List<Reservation> expired = reservationRepository
                .findExpiredHolds(screeningId, ReservationStatus.PENDING, now);
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(r -> r.expire(now));
        reservationRepository.flush();
    }
}
