package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final UserRepository userRepository;
    private final ScreeningRepository screeningRepository;
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;

    @Transactional
    public Reservation create(ReservationCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        Screen screen = screening.getScreen();

        Set<String> requestedSeats = new HashSet<>();
        for (ReservedSeatRequest seat : request.seats()) {
            validateSeat(screen, seat);
            String seatKey = seat.seatRow() + seat.seatNumber();
            if (!requestedSeats.add(seatKey)) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
            boolean alreadyReserved = reservedSeatRepository
                    .existsByReservation_Screening_IdAndSeatRowAndSeatNumberAndReservation_Status(
                            screening.getId(), seat.seatRow(), seat.seatNumber(), ReservationStatus.RESERVED
                    );
            if (alreadyReserved) {
                throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
            }
        }

        Reservation reservation = new Reservation(user, screening);
        for (ReservedSeatRequest seat : request.seats()) {
            reservation.addReservedSeat(new ReservedSeat(
                    reservation, seat.seatRow(), seat.seatNumber()
            ));
        }
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
