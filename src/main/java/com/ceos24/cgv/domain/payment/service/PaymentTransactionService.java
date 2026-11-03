package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentDetailResponse;
import com.ceos24.cgv.domain.payment.dto.response.TossCancelResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancellation;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import com.ceos24.cgv.domain.payment.repository.PaymentCancellationRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.service.command.FullCancellationCommand;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorCode;
import com.ceos24.cgv.domain.reservation.exception.ReservationException;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
//소유자·금액·상태를 검증하고 결제를 승인 요청 처리 상태로 바꿈
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    /**
     * true면 이번 요청이 승인을 시작하고,
     * false면 동일한 결제가 이미 승인된 상태다.
     */
    @Transactional
    //Toss에 승인 요청을 보내기 전에, 결제해도 되는 주문인지 확인하고
    // 다른 요청이 같은 결제를 시작하지 못하게 표시
    public boolean prepareApproval(
            Long userId,
            PaymentConfirmRequest request
    ) {
        ApprovalRows rows = lockApprovalRows(request.orderId());
        Reservation reservation = rows.reservation();
        Payment payment = rows.payment();

        if (!Objects.equals(reservation.getUser().getId(), userId)) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_ACCESS_DENIED
            );
        }

        if (!Objects.equals(payment.getAmount(), request.amount())) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }

        // 같은 주문·금액·결제 키로 승인된 요청은 성공 결과를 재사용한다.
        if (payment.getStatus() == PaymentStatus.DONE
                && Objects.equals(
                payment.getPaymentKey(),
                request.paymentKey()
        )) {
            return false;
        }

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_NOT_APPROVABLE
            );
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new PaymentException(
                    PaymentErrorCode.RESERVATION_NOT_PAYABLE
            );
        }

        if (!reservation.getScreening().getStartTime()
                .isAfter(LocalDateTime.now())) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_WINDOW_CLOSED
            );
        }

        payment.startApproval(request.paymentKey());

        return true;
    }

    private record ApprovalRows(Reservation reservation, Payment payment) {}

    //승인 준비와 완료 모두 예매 -> 결제 순서로 잠기도록 함 .
    private ApprovalRows lockApprovalRows(String orderId) {
        Long reservationId = paymentRepository.findReservationIdByOrderId(orderId)
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND));

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException(
                        ReservationErrorCode.RESERVATION_NOT_FOUND));

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND));

        return new ApprovalRows(reservation, payment);
    }

    @Transactional
    public TossConfirmResponse completeApproval(
            PaymentConfirmRequest request,
            TossConfirmResponse response
    ) {
        ApprovalRows rows = lockApprovalRows(request.orderId());
        Payment payment = rows.payment();

        // 요청했던 주문이 실제로 그 금액만큼 승인되었는지 확인한다.
        if (response == null
                || !"DONE".equals(response.status())
                || response.approvedAt() == null
                || !Objects.equals(payment.getOrderId(), response.orderId())
                || !Objects.equals(payment.getPaymentKey(), response.paymentKey())
                || !Objects.equals(payment.getAmount(), response.totalAmount())) {
            throw new PaymentException(PaymentErrorCode.INVALID_APPROVAL_RESPONSE);
        }

        if (payment.getStatus() != PaymentStatus.APPROVING) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_APPROVABLE);
        }

        payment.completeApproval(response.approvedAt());
        rows.reservation().confirmPayment();

        return response;
    }

    @Transactional(readOnly = true)
    public TossConfirmResponse getApprovedResult(Long userId, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!Objects.equals(payment.getReservation().getUser().getId(), userId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (payment.getStatus() != PaymentStatus.DONE
                || !Objects.equals(payment.getPaymentKey(), request.paymentKey())
                || !Objects.equals(payment.getAmount(), request.amount())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_APPROVABLE);
        }

        return new TossConfirmResponse(
                payment.getPaymentKey(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getApprovedAt()
        );
    }

    @Transactional
    public FullCancellationCommand prepareFullCancellation(
            Long userId,
            String orderId,
            String reason
    ) {
        ApprovalRows rows = lockApprovalRows(orderId);
        Reservation reservation = rows.reservation();
        Payment payment = rows.payment();

        if (!Objects.equals(reservation.getUser().getId(), userId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }

        // 첫 요청이 이미 준비됐다면 새 취소 이력이나 멱등키를 만들지 않는다.
        if (payment.getStatus() == PaymentStatus.CANCELING) {
            PaymentCancellation existing = paymentCancellationRepository
                    .findByPayment_IdAndStatus(
                            payment.getId(),
                            PaymentCancellation.Status.REQUESTED
                    )
                    .orElseThrow(() -> new PaymentException(
                            PaymentErrorCode.PAYMENT_NOT_CANCELABLE
                    ));

            if (!Objects.equals(existing.getReason(), reason)) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
            }

            return FullCancellationCommand.from(payment, existing);
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        payment.startFullCancellation();

        PaymentCancellation cancellation = paymentCancellationRepository.save(
                PaymentCancellation.requestFull(payment, reason)
        );

        return FullCancellationCommand.from(payment, cancellation);
    }

    //동일한 취소 결과가 두번 도착해도 좌석은 한번만 반환하도록
    // 취소 이력이 이미 DONE인지 좌석 조회보다 먼저 검사해야 함 .

    @Transactional
    public PaymentDetailResponse completeFullCancellation(
            String orderId,
            Long cancellationId,
            TossCancelResponse response
    ) {
        ApprovalRows rows = lockApprovalRows(orderId);
        Payment payment = rows.payment();
        Reservation reservation = rows.reservation();

        PaymentCancellation cancellation = paymentCancellationRepository
                .findById(cancellationId)
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.PAYMENT_NOT_CANCELABLE
                ));

        if (!Objects.equals(cancellation.getPayment().getId(), payment.getId())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        if (cancellation.getStatus() == PaymentCancellation.Status.DONE) {
            return PaymentDetailResponse.from(payment);
        }

        TossCancelResponse.CancelDetail refund =
                validateFullRefund(payment, cancellation, response);

        List<Long> seatIds = reservation.getReservationSeats().stream()
                .map(item -> item.getSeat().getId())
                .sorted()
                .toList();

        if (seatIds.isEmpty()) {
            throw new IllegalStateException("반환할 예매 좌석 이력이 없습니다.");
        }

        List<ScreeningSeat> screeningSeats = screeningSeatRepository.findAllForUpdate(
                reservation.getScreening().getId(), seatIds
        );

        if (screeningSeats.size() != seatIds.size()) {
            throw new IllegalStateException("예매 좌석과 상영 회차 좌석이 일치하지 않습니다.");
        }

        // 하나라도 실패하면 취소 이력,결제,예매,좌석 변경을 함께 롤백한다.
        payment.completeFullCancellation(refund.cancelAmount());
        cancellation.complete(refund.transactionKey(), refund.canceledAt());
        reservation.cancelAfterFullRefund();
        screeningSeats.forEach(ScreeningSeat::release);

        return PaymentDetailResponse.from(payment);
    }

    private TossCancelResponse.CancelDetail validateFullRefund(
            Payment payment,
            PaymentCancellation cancellation,
            TossCancelResponse response
    ) {
        if (response == null
                || !Objects.equals(response.orderId(), payment.getOrderId())
                || !Objects.equals(response.paymentKey(), payment.getPaymentKey())
                || !Objects.equals(response.totalAmount(), payment.getAmount())
                || !"CANCELED".equals(response.status())
                || !Objects.equals(response.balanceAmount(), 0)
                || response.cancels() == null) {
            throw new PaymentException(PaymentErrorCode.INVALID_CANCEL_RESPONSE);
        }

        return response.cancels().stream()
                .filter(Objects::nonNull)
                .filter(item -> "DONE".equals(item.cancelStatus()))
                .filter(item -> Objects.equals(item.cancelAmount(), cancellation.getAmount()))
                .filter(item -> Objects.equals(item.cancelReason(), cancellation.getReason()))
                .filter(item -> item.transactionKey() != null && !item.transactionKey().isBlank())
                .filter(item -> item.canceledAt() != null)
                .findFirst()
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.INVALID_CANCEL_RESPONSE
                ));
    }

}