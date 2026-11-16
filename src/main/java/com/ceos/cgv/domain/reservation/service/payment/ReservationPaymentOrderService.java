package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.domain.payment.entity.PaymentOrderSeat;
import com.ceos.cgv.domain.payment.repository.PaymentOrderRepository;
import com.ceos.cgv.domain.payment.repository.PaymentRepository;
import com.ceos.cgv.domain.payment.service.PaymentQueryService;
import com.ceos.cgv.domain.payment.service.PaymentResponses;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.domain.reservation.config.ReservationPaymentPricingProperties;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationPaymentOrderService {
    private static final int MAX_ORDER_NAME_LENGTH = 100;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationPaymentService reservationPayments;
    private final ReservationPaymentPricingProperties pricingProperties;
    private final PaymentResponses paymentResponses;
    private final Clock paymentClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentOrderCreationResult createOrder(Long userId, PaymentOrderCreateRequest request) {
        if (request == null || request.reservationId() == null || request.reservationId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        PaymentReservationSnapshot snapshot =
                reservationPayments.lockOwned(request.reservationId(), userId);
        Optional<PaymentOrder> existing =
                paymentOrderRepository.findByReservationId(request.reservationId());
        if (existing.isPresent())
            return new PaymentOrderCreationResult(
                    false,
                    paymentResponses.order(
                            paymentRepository
                                    .findByOrder_Id(existing.get().getId())
                                    .orElseThrow()));
        if (snapshot.status() != ReservationStatus.HELD)
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        if (snapshot.expiresAt() == null || !paymentClock.instant().isBefore(snapshot.expiresAt()))
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        List<PaymentOrderSeat> seats =
                snapshot.seats().stream()
                        .filter(seat -> seat.canceledAt() == null)
                        .map(
                                seat ->
                                        PaymentOrderSeat.builder()
                                                .reservedSeatId(seat.reservedSeatId())
                                                .seatRow(seat.seatRow())
                                                .seatNumber(seat.seatNumber())
                                                .build())
                        .toList();
        PaymentOrder order =
                paymentOrderRepository.save(
                        PaymentOrder.builder()
                                .orderId(UUID.randomUUID().toString())
                                .reservationId(snapshot.reservationId())
                                .userId(userId)
                                .orderName(createOrderName(snapshot.movieTitle(), seats.size()))
                                .seatPrice(pricingProperties.seatPrice())
                                .expiresAt(snapshot.expiresAt())
                                .seats(seats)
                                .build());
        return new PaymentOrderCreationResult(
                true,
                paymentResponses.order(
                        paymentRepository.saveAndFlush(Payment.builder().order(order).build())));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findPaymentId(Long userId, Long reservationId) {
        return paymentRepository
                .findByOrder_ReservationId(reservationId)
                .map(payment -> {
                    PaymentQueryService.owned(payment, userId);
                    return payment.getId();
                });
    }

    private static String createOrderName(String movieTitle, int seatCount) {
        String suffix = " " + seatCount + "석";
        int titleEnd = Math.min(movieTitle.length(), MAX_ORDER_NAME_LENGTH - suffix.length());
        // Java와 JavaScript의 UTF-16 길이 한도를 지키면서 서로게이트 쌍을 보존한다.
        if (titleEnd > 0
                && titleEnd < movieTitle.length()
                && Character.isHighSurrogate(movieTitle.charAt(titleEnd - 1))
                && Character.isLowSurrogate(movieTitle.charAt(titleEnd))) {
            titleEnd--;
        }
        return movieTitle.substring(0, titleEnd) + suffix;
    }
}
