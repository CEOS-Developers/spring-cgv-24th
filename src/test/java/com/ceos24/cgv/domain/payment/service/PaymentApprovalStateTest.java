package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import com.ceos24.cgv.domain.payment.repository.PaymentCancellationRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentCancellationSeatRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalStateTest {

    @Mock PaymentRepository payments;
    @Mock ReservationRepository reservations;
    @Mock PaymentCancellationRepository cancellations;
    @Mock ScreeningSeatRepository seats;
    @Mock PaymentCancellationSeatRepository cancellationSeats;

    private PaymentTransactionService service;
    private Payment payment;
    private PaymentConfirmRequest request;

    @BeforeEach
    void setUp() {
        service = new PaymentTransactionService(
                payments, reservations, cancellations, seats, cancellationSeats);
        Reservation reservation = mock(Reservation.class);
        given(reservation.getStatus()).willReturn(ReservationStatus.PENDING_PAYMENT);
        given(reservation.getTotalPrice()).willReturn(10000);
        payment = Payment.create(reservation);
        payment.startApproval("old-key");
        request = new PaymentConfirmRequest("old-key", payment.getOrderId(), 10000);

        given(payments.findReservationIdByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(1L));
        given(reservations.findByIdForUpdate(1L)).willReturn(Optional.of(reservation));
        given(payments.findByOrderIdForUpdate(payment.getOrderId()))
                .willReturn(Optional.of(payment));
    }

    @Test
    void confirmedAbortedPaymentBecomesRetryable() {
        service.markApprovalUnknown(request);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);

        service.completeFailedApproval(request, new TossConfirmResponse(
                "old-key", payment.getOrderId(), 10000, "ABORTED", null));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThatThrownBy(() -> payment.startApproval("old-key"))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_APPROVABLE);
        payment.startApproval("new-key");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(payment.getPaymentKey()).isEqualTo("new-key");
    }

    @Test
    void unrelatedFailedResponseCannotChangePayment() {
        service.markApprovalUnknown(request);

        assertThatThrownBy(() -> service.completeFailedApproval(request,
                new TossConfirmResponse("another-key", payment.getOrderId(),
                        10000, "EXPIRED", null)))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_APPROVAL_RESPONSE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void successfulRecoveryCanFinishUnknownApproval() {
        service.markApprovalUnknown(request);
        OffsetDateTime approvedAt = OffsetDateTime.now();
        service.completeApproval(request, new TossConfirmResponse(
                "old-key", payment.getOrderId(), 10000, "DONE", approvedAt));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        verify(payment.getReservation()).confirmPayment();
    }
}
