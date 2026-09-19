package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final UserRepository userRepository;
    private final ScreeningRepository screeningRepository;
    private final MovieRepository movieRepository;
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;

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

        // 같은 상영 일정에 대한 동시 예매를 순서대로 처리하기 위해 행을 잠금
        Screening screening = screeningRepository.findByIdWithLock(request.screeningId())
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

        // 같은 회차의 요청 좌표만 한 번의 존재 조회로 확인
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
                    .build());
        }

        // Reservation의 cascade 설정을 이용해 예매와 좌석을 함께 저장
        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long reservationId) {
        return reservationRepository.findWithSeatsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    @Transactional
    public void cancel(Long reservationId) {
        Reservation reservation = findById(reservationId);
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
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
