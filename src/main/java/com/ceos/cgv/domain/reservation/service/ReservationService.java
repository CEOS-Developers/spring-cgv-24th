package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final UserRepository userRepository;
    private final ScreeningRepository screeningRepository;
    private final MovieRepository movieRepository;
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final ScreeningSeatLockService screeningSeatLockService;

    // 예매 생성 전체를 하나의 트랜잭션으로 처리하고 커밋된 데이터만 읽음
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(ReservationCreateRequest request) {
        // 예매를 요청한 사용자가 실제로 존재하는지 확인
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long movieId = screeningRepository.findMovieIdById(request.screeningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        Movie movie = movieRepository.findByIdForShare(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        movie.ensurePublic();

        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));

        // 좌석 유효성 검증에 필요한 상영관 좌석 구조를 가져옴
        Screen screen = screening.getScreen();

        if (request.seats() == null || request.seats().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 모든 좌석을 검증한 뒤 요청 좌표의 중복 여부를 확인
        Set<SeatCoordinate> requestedSeats = new HashSet<>();
        for (ReservedSeatRequest seat : request.seats()) {
            // 상영관의 좌석 범위를 벗어난 좌석인지 확인
            validateSeat(screen, seat);

            if (!requestedSeats.add(new SeatCoordinate(seat.seatRow(), seat.seatNumber()))) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
        }

        long seatCount = screeningSeatRepository.countByScreening_Id(screening.getId());
        boolean legacyScreening = seatCount == 0;
        if (legacyScreening) {
            // 이관 전 회차의 기존 직렬화 경로는 좌석 데이터가 준비될 때까지 유지한다.
            screeningRepository.findByIdWithLock(screening.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        } else if (seatCount != (long) screen.getRowCount() * screen.getSeatsPerRow()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }

        List<ScreeningSeat> lockedSeats = legacyScreening ? List.of()
                : screeningSeatLockService.lockSeats(screening.getId(), requestedSeats);
        Map<SeatCoordinate, ScreeningSeat> screeningSeatsByCoordinate = new HashMap<>();
        for (ScreeningSeat seat : lockedSeats) {
            screeningSeatsByCoordinate.put(new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()), seat);
            if (seat.getCurrentReservation() != null) {
                throw new BusinessException(seat.getCurrentReservation().getStatus() == ReservationStatus.HELD
                        ? ErrorCode.SEAT_HELD : ErrorCode.SEAT_ALREADY_RESERVED);
            }
        }

        // 기존 이력 조회도 유지해 이관 전 좌석 점유와 데이터 불일치를 방어한다.
        if (reservedSeatRepository.existsReservedByScreeningIdAndCoordinates(
                screening.getId(), requestedSeats)) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
        }

        // 사용자와 상영 일정을 연결한 예매 엔티티 객체를 생성함
        Reservation reservation = Reservation.builder()
                .user(user)
                .screening(screening)
                .build();

        // 요청받은 좌석마다 예매 좌석 자식 엔티티를 만듦
        for (ReservedSeatRequest seat : request.seats()) {
            reservation.addReservedSeat(ReservedSeat.builder()
                    .reservation(reservation)
                    .seatRow(seat.seatRow())
                    .seatNumber(seat.seatNumber())
                    .screeningSeat(screeningSeatsByCoordinate.get(
                            new SeatCoordinate(seat.seatRow(), seat.seatNumber())))
                    .build());
        }

        // Reservation의 cascade 설정을 이용해 예매와 좌석을 함께 저장
        Reservation saved = reservationRepository.save(reservation);
        for (ScreeningSeat seat : lockedSeats) {
            seat.occupy(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findWithSeatsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return reservation;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long reservationId, Long userId) {
        Reservation reservation = findById(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        List<ReservedSeat> histories = reservation.getReservedSeats();
        long linkedCount = histories.stream().filter(seat -> seat.getScreeningSeat() != null).count();
        if (linkedCount > 0 && linkedCount != histories.size()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        if (linkedCount > 0) {
            List<SeatCoordinate> coordinates = histories.stream()
                    .map(seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()))
                    .toList();
            List<ScreeningSeat> locked = screeningSeatLockService.lockSeats(
                    reservation.getScreening().getId(), coordinates);
            for (ScreeningSeat seat : locked) {
                seat.releaseIfOwnedBy(reservation);
            }
        }
        reservation.cancel();
    }

    private void validateSeat(Screen screen, ReservedSeatRequest seat) {
        int rowNumber = seat.seatRow().charAt(0) - 'A' + 1;
        if (rowNumber > screen.getRowCount() || seat.seatNumber() > screen.getSeatsPerRow()) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
    }
}
