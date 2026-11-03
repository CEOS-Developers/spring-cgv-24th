package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.*;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentConfirmTransactionService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    // 외부 호출에 필요한 값만 전달한다. JPA 엔티티는 전달하지 않는다.
    public record ConfirmContext(
            Long userId,
            Long reservationId,
            String orderId,
            String paymentKey,
            Long amount,
            String idempotencyKey,
            boolean lookupOnly,
            PaymentConfirmResponse completed
    ) {
    }

    @Transactional
    public ConfirmContext begin(
            Long userId,
            PaymentConfirmRequest request
    ) {
        Long reservationId = paymentRepository.findTargetId(
                request.orderId(),
                userId,
                PaymentTargetType.RESERVATION
        ).orElseThrow(() ->
                new GlobalException(ErrorCode.PAYMENT_NOT_FOUND)
        );

        // 항상 예매 -> 결제 순서로 잠근다.
        Reservation reservation = reservationRepository
                .findMyReservationWithLock(reservationId, userId)
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)
                );

        Payment payment = findPayment(request.orderId(), userId);

        validateRequest(payment, reservation, request);

        // 같은 완료 요청이면 토스를 다시 호출하지 않는다.
        if (payment.getStatus() == PaymentStatus.DONE) {
            if (reservation.getStatus() != ReservationStatus.Reserved) {
                throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
            }

            return context(
                    userId, reservation, payment, false,
                    PaymentConfirmResponse.from(payment, reservation)
            );
        }

        if (payment.getStatus() == PaymentStatus.READY) {
            LocalDateTime now = LocalDateTime.now();

            if (!payment.getExpiresAt().isAfter(now)) {
                throw new GlobalException(ErrorCode.RESERVATION_EXPIRED);
            }

            reservation.beginPayment(now);
            payment.startProcessing(request.paymentKey());

        } else if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            validateHeldReservation(reservation);

            // 같은 paymentKey·멱등키로만 재시도한다.
            payment.startProcessing(request.paymentKey());

        } else if (payment.getStatus() == PaymentStatus.PROCESSING) {
            validateHeldReservation(reservation);

            // 다른 요청이 승인 중이면 추가 승인 대신 조회한다.
            return context(
                    userId, reservation, payment, true, null
            );

        } else {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        return context(userId, reservation, payment, false, null);
    }

    @Transactional
    public PaymentConfirmResponse finish(
            ConfirmContext context,
            TossPaymentConfirmResponse remote
    ) {
        Reservation reservation = reservationRepository
                .findMyReservationWithLock(
                        context.reservationId(),
                        context.userId()
                )
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)
                );

        Payment payment = findPayment(
                context.orderId(), context.userId()
        );

        // 다른 요청이 이미 결과를 반영한 경우 상태를 되돌리지 않는다.
        if (payment.getStatus() == PaymentStatus.DONE
                || payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentConfirmResponse.from(payment, reservation);
        }

        validateHeldReservation(reservation);

        if (!matches(payment, remote)) {
            payment.markUnknown();
            return PaymentConfirmResponse.from(payment, reservation);
        }

        if ("DONE".equals(remote.status())
                && remote.approvedAt() != null) {

            LocalDateTime approvedAt = remote.approvedAt()
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime();

            payment.complete(approvedAt);
            reservation.completePayment();

        } else if ("ABORTED".equals(remote.status())
                || "EXPIRED".equals(remote.status())) {

            // 토스에서 실패 및 만료가 확인된 경우에만 실패 확정
            payment.markFailed();
            reservation.endPaymentProcessing();

            if (reservation.expireIfPending(LocalDateTime.now())) {
                reservationSeatRepository
                        .deleteAllByReservation_ReservationId(
                                reservation.getReservationId()
                        );
            }

        } else {
            // 진행 중, 미지원 상태, 불완전한 응답은 완료로 처리하지 않는다.
            payment.markUnknown();
        }

        return PaymentConfirmResponse.from(payment, reservation);
    }

    private Payment findPayment(String orderId, Long userId) {
        return paymentRepository
                .findMyPaymentWithLock(orderId, userId)
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.PAYMENT_NOT_FOUND)
                );
    }

    private void validateRequest(
            Payment payment,
            Reservation reservation,
            PaymentConfirmRequest request
    ) {
        boolean mismatch =
                payment.getTargetType() != PaymentTargetType.RESERVATION
                        || !Objects.equals(
                        payment.getTargetId(),
                        reservation.getReservationId()
                )
                        || !Objects.equals(payment.getAmount(), request.amount())
                        || reservation.getTotalPrice() == null
                        || payment.getAmount().longValue()
                        != reservation.getTotalPrice().longValue()
                        || (payment.getPaymentKey() != null
                        && !payment.getPaymentKey().equals(request.paymentKey()));

        if (mismatch) {
            throw new GlobalException(
                    ErrorCode.PAYMENT_INFORMATION_MISMATCH
            );
        }
    }

    private void validateHeldReservation(Reservation reservation) {
        if (!reservation.isPaymentProcessing()
                || reservation.getStatus() != ReservationStatus.Pending) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    private boolean matches(
            Payment payment,
            TossPaymentConfirmResponse remote
    ) {
        return remote != null
                && Objects.equals(
                payment.getPaymentKey(), remote.paymentKey()
        )
                && Objects.equals(
                payment.getOrderId(), remote.orderId()
        )
                && Objects.equals(
                payment.getAmount(), remote.totalAmount()
        );
    }

    private ConfirmContext context(
            Long userId,
            Reservation reservation,
            Payment payment,
            boolean lookupOnly,
            PaymentConfirmResponse completed
    ) {
        return new ConfirmContext(
                userId,
                reservation.getReservationId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getAmount(),
                payment.getConfirmIdempotencyKey(),
                lookupOnly,
                completed
        );
    }
}