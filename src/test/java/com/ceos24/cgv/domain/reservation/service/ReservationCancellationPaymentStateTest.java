package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationPaymentStateTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ScreeningSeatRepository screeningSeatRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ReservationService reservationService;

    @Test
    void approvingPaymentCannotReleaseReservationSeats() {
        User user = org.mockito.Mockito.mock(User.class);
        Reservation reservation = org.mockito.Mockito.mock(Reservation.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(reservationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(reservation));
        given(reservation.getUser()).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(paymentRepository.findByReservationIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payment.getStatus()).willReturn(PaymentStatus.APPROVING);

        assertThatThrownBy(() -> reservationService.cancelReservation(1L, 10L))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_DIRECTLY_CANCELABLE);

        var order = inOrder(reservationRepository, paymentRepository);
        order.verify(reservationRepository).findByIdForUpdate(10L);
        order.verify(paymentRepository).findByReservationIdForUpdate(10L);
        verifyNoInteractions(screeningSeatRepository);
    }
}
