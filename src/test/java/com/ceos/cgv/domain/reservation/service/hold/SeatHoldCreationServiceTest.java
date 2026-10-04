package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SeatHoldCreationServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ScreeningRepository screeningRepository;
    @Mock private ScreeningSeatRepository screeningSeatRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private ScreeningSeatLockService seatLockService;

    @Test
    void 요청_키가_없으면_DB_조회와_잠금_전에_거부한다() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC);
        SeatHoldCreationService service = new SeatHoldCreationService(
                userRepository, reservationRepository, screeningRepository, screeningSeatRepository,
                movieRepository, reservedSeatRepository, seatLockService,
                new SeatHoldProperties(Duration.ofMinutes(5), 8, 1, 100), clock);
        SeatHoldCreateRequest request = new SeatHoldCreateRequest(
                8L, List.of(new ReservedSeatRequest("A", 1)));

        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(userRepository, reservationRepository, screeningRepository, screeningSeatRepository,
                movieRepository, reservedSeatRepository, seatLockService);
    }
}
