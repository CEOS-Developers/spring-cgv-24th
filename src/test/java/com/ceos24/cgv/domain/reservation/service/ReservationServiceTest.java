package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationException;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock private ReservationRepository reservationRepository;
    @Mock private ScreeningRepository screeningRepository;
    @Mock private ScreeningSeatRepository screeningSeatRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @InjectMocks private ReservationService reservationService;

    @Test
    void createReservationReservesSeatsAndCreatesReadyPayment() {
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        ScreenType screenType = mock(ScreenType.class);
        Seat seat = mock(Seat.class);
        ScreeningSeat screeningSeat = ScreeningSeat.create(screening, seat);

        given(user.getId()).willReturn(1L);
        given(screening.getId()).willReturn(100L);
        given(screening.getStartTime()).willReturn(LocalDateTime.now().plusDays(1));
        given(screening.getScreen()).willReturn(screen);
        given(screen.getId()).willReturn(200L);
        given(screen.getScreenType()).willReturn(screenType);
        given(screenType.getBasePrice()).willReturn(15000);
        given(seat.getId()).willReturn(10L);
        given(seat.getScreen()).willReturn(screen);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(screeningRepository.findById(100L)).willReturn(Optional.of(screening));
        given(screeningSeatRepository.findAllForUpdate(100L, List.of(10L)))
                .willReturn(List.of(screeningSeat));
        given(reservationRepository.save(any(Reservation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.createReservation(
                1L, new ReservationCreateRequest(100L, List.of(10L)));

        assertThat(screeningSeat.isReserved()).isTrue();
        assertThat(response.totalPrice()).isEqualTo(15000);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.orderId()).isNotBlank();
        assertThat(response.seats()).hasSize(1);
    }

    @Test
    void createReservationRejectsReservedSeat() {
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        Seat seat = mock(Seat.class);
        ScreeningSeat screeningSeat = ScreeningSeat.create(screening, seat);
        screeningSeat.reserve();

        given(screening.getId()).willReturn(100L);
        given(screening.getStartTime()).willReturn(LocalDateTime.now().plusDays(1));
        given(screening.getScreen()).willReturn(screen);
        given(screen.getId()).willReturn(200L);
        given(seat.getScreen()).willReturn(screen);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(screeningRepository.findById(100L)).willReturn(Optional.of(screening));
        given(screeningSeatRepository.findAllForUpdate(100L, List.of(10L)))
                .willReturn(List.of(screeningSeat));

        assertThatThrownBy(() -> reservationService.createReservation(
                1L, new ReservationCreateRequest(100L, List.of(10L))))
                .isInstanceOf(ReservationException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void cancelReadyReservationReleasesSeat() {
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Seat seat = mock(Seat.class);
        Payment payment = mock(Payment.class);
        Reservation reservation = Reservation.create(user, screening);
        reservation.addReservationSeat(ReservationSeat.create(seat, 15000));
        ScreeningSeat screeningSeat = ScreeningSeat.create(screening, seat);
        screeningSeat.reserve();

        given(user.getId()).willReturn(1L);
        given(screening.getId()).willReturn(100L);
        given(seat.getId()).willReturn(10L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(reservationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(reservation));
        given(paymentRepository.findByReservationIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payment.getStatus()).willReturn(PaymentStatus.READY);
        given(screeningSeatRepository.findAllForUpdate(100L, List.of(10L)))
                .willReturn(List.of(screeningSeat));

        reservationService.cancelReservation(1L, 10L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(screeningSeat.isReserved()).isFalse();
    }
}
