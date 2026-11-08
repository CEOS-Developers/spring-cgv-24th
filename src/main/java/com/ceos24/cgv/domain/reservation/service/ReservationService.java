package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRetryTarget;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest.SeatRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationDetailRow;
import com.ceos24.cgv.domain.reservation.dto.ReservationPaymentAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationRecoveryTarget;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    // 예매 하나에 승인된 결제는 많아야 하나다. 승인 시작이 같은 예매의 다른 승인 중 시도를 막는다.
    private static final List<PaymentStatus> PAID_STATUSES =
            List.of(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED);
    // 토스 결제 내역에 남는 사유다. 회원이 취소한 것이 아니라는 점이 드러나야 한다.
    private static final String LOST_HOLD_REFUND_REASON = "좌석 선점 만료로 자동 환불";

    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final UserRepository userRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final Clock clock;

    @Transactional
    public ReservationResponse create(Long userId, ReservationCreateRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 응답이 읽는 영화·상영관·지점까지 함께 로딩한다
        Screening screening = screeningRepository.findByIdWithDetails(req.screeningId())
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));
        // 토큰은 만료 전까지 유효하므로 탈퇴한 사용자의 토큰도 여기까지 온다
        User user = userRepository.getByIdOrThrow(userId);

        // 만료 선점 정리는 검증 뒤에 한다. 범위·중복 검증을 통과해야 좌석 키가 안전한 범위 안이다.
        validateSeats(screening.getTheater().getTheaterType(), req.seats());
        releaseExpiredHolds(screening.getId(), req.seats(), now);
        ensureNotOccupied(screening.getId(), req.seats(), now);

        Reservation reservation = Reservation.builder()
                .user(user).screening(screening).now(now).build();
        // 좌석 순서가 곧 INSERT 순서이고, INSERT 순서가 곧 락 획득 순서다.
        // 정렬해 두면 [A1,A2]와 [A2,A1] 요청이 서로를 물고 도는 데드락이 생길 수 없다.
        orderedSeats(req.seats()).forEach(s -> reservation.addSeat(s.rowNum(), s.colNum(), s.audienceType()));

        return ReservationResponse.from(saveHold(reservation), now);
    }

    // 결제창을 열기 전에 주문번호와 받을 금액을 저장해 둔다. 승인 요청의 금액은 이 값과 대조한다.
    // 거절된 뒤 다시 결제할 때도 새 주문을 만든다. 토스 orderId는 재사용할 수 없다.
    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long id, Long userId) {
        Reservation reservation = findOwnedForUpdate(id, userId);
        reservation.verifyPayable(LocalDateTime.now(clock));
        ensureNoPaymentInProgress(id);

        Payment payment = paymentRepository.save(Payment.builder().reservation(reservation).build());
        return PaymentOrderResponse.of(payment, orderNameOf(reservation));
    }

    // 승인 T1. 토스를 부르기 전에 거를 수 있는 것을 모두 거르고, 승인 중임을 커밋해 둔다.
    // 여기서 예외가 나면 토스를 부르지 않으므로 돈이 빠지지 않는다.
    @Transactional
    public ReservationPaymentAttempt startPayment(Long id, Long userId, PaymentConfirmRequest req) {
        Reservation reservation = findOwnedForUpdate(id, userId);
        Payment payment = paymentRepository.findByOrderIdAndReservationId(req.orderId(), id)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.verifyAmount(req.amount());
        // 같은 예매의 다른 시도가 승인 중이면 막는다. 두 탭에서 각각 인증을 마치면 시도마다 주문번호가 달라
        // 토스 멱등키로는 걸러지지 않고 두 번 결제된다.
        ensureNoPaymentInProgress(id);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAtBeforePayment = reservation.getExpiresAt();
        reservation.holdForPayment(now);
        payment.startApproval(req.paymentKey(), now);

        return new ReservationPaymentAttempt(payment.getId(), id, userId, req.paymentKey(),
                payment.getOrderId(), payment.getAmount(), expiresAtBeforePayment);
    }

    // 승인 T2 (성공). 토스는 이미 승인했으므로 여기서 실패하면 돈은 빠졌는데 예매가 확정되지 않는다.
    // 롤백되면 결제가 IN_PROGRESS로 남아, 결제 조회로 실제 상태를 맞출 단서가 된다.
    // 예매 잠금이 첫 쿼리다. T2가 늦어져 정리가 먼저 반영했으면 결제 상태 검사에서 막혀 덮어쓰지 않는다.
    @Transactional
    public ReservationResponse completePayment(ReservationPaymentAttempt attempt, TossPayment approved) {
        LocalDateTime now = LocalDateTime.now(clock);
        findOwnedForUpdate(attempt.reservationId(), attempt.userId());
        Payment payment = findPayment(attempt.paymentId());
        Reservation reservation = findOwnedWithDetails(attempt.reservationId(), attempt.userId());

        payment.approve(approved.method(), approved.approvedAtIn(clock.getZone(), now));
        reservation.confirm(now);
        return ReservationResponse.from(reservation, now);
    }

    // 승인 T2 (거절). 좌석은 놓지 않는다. 카드 한도 초과처럼 다른 수단으로 바꾸면 되는 거절이 많고,
    // 다시 시도할 시간은 선점 만료가 제한한다. 성공 T2와 같은 이유로 예매 잠금이 첫 쿼리다.
    @Transactional
    public void failPayment(ReservationPaymentAttempt attempt, String failureCode, String failureMessage) {
        Reservation reservation = findOwnedForUpdate(attempt.reservationId(), attempt.userId());
        Payment payment = findPayment(attempt.paymentId());

        payment.fail(failureCode, failureMessage);
        reservation.endPaymentHold(attempt.expiresAtBeforePayment());
    }

    // 승인을 시작한 지 RECOVERY_DELAY가 지났는데 아직 승인 중인 결제. 원래 요청의 T2가 실패했거나 서버가 그 사이 죽었다.
    public List<String> findStaleApprovalOrderIds() {
        return paymentRepository.findReservationOrderIdsByStatusStartedBefore(
                PaymentStatus.IN_PROGRESS, LocalDateTime.now(clock).minus(Payment.RECOVERY_DELAY));
    }

    // 토스에 묻기 전에 정리해도 되는 결제인지 확인한다. 관리자 요청도 이 검사를 거친다.
    public ReservationRecoveryTarget findRecoveryTarget(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .filter(p -> p.getReservation() != null)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.verifyRecoverable(LocalDateTime.now(clock));
        Reservation reservation = payment.getReservation();
        return new ReservationRecoveryTarget(payment.getId(), reservation.getId(), reservation.getUser().getId(),
                payment.getOrderId(), payment.getPaymentKey(), payment.getAmount());
    }

    // 토스가 승인한 것으로 확인됐다. 결제를 확정하고, 좌석이 남아 있으면 예매를 확정한다.
    // 좌석을 잃었으면(선점 만료 후 다른 요청이 EXPIRED로 바꿈) 전액 환불 요청을 같은 트랜잭션에서 저장해 돌려준다.
    // 승인 확정과 환불 요청을 함께 커밋해야, 토스 취소 전에 서버가 죽어도 남은 취소 요청을 재시도로 마칠 수 있다.
    // 예매 잠금이 첫 쿼리다. 원래 요청의 T2나 다른 정리와 겹쳐도 결제 상태를 다시 확인해 한 번만 반영한다.
    @Transactional
    public Optional<ReservationCancelAttempt> applyRecoveredApproval(ReservationRecoveryTarget target,
                                                                     TossPayment approved) {
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = findOwnedForUpdate(target.reservationId(), target.userId());
        Payment payment = findPayment(target.paymentId());
        payment.verifyRecoverable(now);

        payment.approve(approved.method(), approved.approvedAtIn(clock.getZone(), now));
        if (reservation.isPending()) {
            reservation.confirmAfterRecovery(now);
            return Optional.empty();
        }
        PaymentCancel cancel = paymentCancelRepository.save(PaymentCancel.builder()
                .payment(payment)
                .amount(payment.getAmount())
                .reason(LOST_HOLD_REFUND_REASON)
                .now(now)
                .build());
        return Optional.of(attemptOf(cancel, reservation.getId(), target.userId()));
    }

    // 토스가 승인하지 않은 것이 확정됐다. 늘린 선점 시간은 되돌리지 않는다. 정리는 승인 시작 후
    // RECOVERY_DELAY가 지난 뒤라 늘린 3분도 대부분 지났고, 되돌릴 기준 시각은 원래 요청의 메모리에만 있었다.
    @Transactional
    public void failRecoveredPayment(ReservationRecoveryTarget target, String failureCode, String failureMessage) {
        findOwnedForUpdate(target.reservationId(), target.userId());
        Payment payment = findPayment(target.paymentId());
        payment.verifyRecoverable(LocalDateTime.now(clock));
        payment.fail(failureCode, failureMessage);
    }

    // 요청한 지 RECOVERY_DELAY가 지났는데 결과를 모르는 취소. 회원이 재시도하지 않아도 같은 멱등키로 다시 보낸다.
    public List<ReservationCancelRetryTarget> findStaleCancelTargets() {
        LocalDateTime now = LocalDateTime.now(clock);
        return paymentCancelRepository.findReservationCancelsByStatusRequestedBetween(PaymentCancelStatus.IN_PROGRESS,
                        now.minus(PaymentCancelRepository.AUTO_RETRY_PERIOD), now.minus(Payment.RECOVERY_DELAY)).stream()
                .map(cancel -> {
                    Reservation reservation = cancel.getPayment().getReservation();
                    return new ReservationCancelRetryTarget(reservation.getId(), reservation.getUser().getId(), cancel.getId());
                })
                .toList();
    }

    // 조회는 상태를 바꾸지 않으므로 엔티티가 필요 없다. 프로젝션으로 받으면 응답이 쓰는
    // 스칼라만 읽고, 영속성 컨텍스트에 엔티티와 더티 체킹 스냅샷도 남지 않는다.
    public ReservationResponse getById(Long id, Long userId) {
        List<ReservationDetailRow> rows = reservationRepository.findOwnedDetailRows(id, userId);
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return ReservationResponse.of(rows, LocalDateTime.now(clock));
    }

    // 취소·만료된 예매도 상태를 달아 보여준다. 사라지면 사용자는 취소가 됐는지 확인할 곳이 없다.
    // 탈퇴한 사용자의 토큰을 빈 목록으로 받아 주면 없는 사용자가 "예매 없음"으로 숨는다.
    public List<ReservationResponse> findMine(Long userId) {
        userRepository.validateExists(userId);
        return ReservationResponse.listOf(
                reservationRepository.findDetailRowsByUserId(userId), LocalDateTime.now(clock));
    }

    // 거절된 시도도 보여준다. 회원이 "왜 결제가 안 됐나"를 확인할 곳이 여기뿐이다.
    public List<PaymentResponse> findPayments(Long id, Long userId) {
        if (!reservationRepository.existsByIdAndUserId(id, userId)) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        List<Payment> payments = paymentRepository.findByReservationIdAndStatusNotOrderByIdDesc(id, PaymentStatus.READY);
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PaymentCancel>> cancels = paymentCancelRepository
                .findByPaymentIdInOrderByIdAsc(payments.stream().map(Payment::getId).toList()).stream()
                .collect(Collectors.groupingBy(cancel -> cancel.getPayment().getId()));
        return payments.stream()
                .map(payment -> PaymentResponse.of(payment, cancels.getOrDefault(payment.getId(), List.of())))
                .toList();
    }

    // 취소 T1. seatKeys가 null이면 전체 취소, 아니면 그 좌석만 취소한다.
    // 결제 전 선점은 돌려줄 돈이 없어 여기서 바로 취소하고 빈 값을 돌려준다.
    // 결제된 예매는 취소 요청을 저장하고 좌석을 그 요청에 묶어 둔다. 좌석은 환불이 확인된 T2에서 놓는다.
    // 승인 중인 선점을 취소하면 토스가 승인한 뒤 확정할 예매가 없다. 승인 시작과 같은 예매 행 잠금으로 순서를 정한다.
    @Transactional
    public Optional<ReservationCancelAttempt> startCancel(Long id, Long userId, List<Integer> seatKeys) {
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = findOwnedForUpdate(id, userId);
        ensureNoPaymentInProgress(id);
        if (seatKeys != null && seatKeys.stream().distinct().count() != seatKeys.size()) {
            throw new CustomException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        }
        if (seatKeys == null && !reservation.isReserved()) {
            reservation.cancel(now);
            return Optional.empty();
        }

        List<ReservationSeat> targets = reservation.selectSeatsToRefund(seatKeys, now);
        Payment payment = paymentRepository.findFirstByReservationIdAndStatusIn(id, PAID_STATUSES)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        // 같은 결제의 취소는 한 번에 하나다. 두 요청이 각자 잔액을 보고 토스를 부르면 둘 다 통과할 수 있다.
        if (paymentCancelRepository.existsByPaymentIdAndStatus(payment.getId(), PaymentCancelStatus.IN_PROGRESS)) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
        }

        PaymentCancel cancel = paymentCancelRepository.save(PaymentCancel.builder()
                .payment(payment)
                .amount(targets.stream().mapToInt(ReservationSeat::getPaidPrice).sum())
                .reason(cancelReasonOf(targets, seatKeys == null))
                .now(now)
                .build());
        reservation.requestRefund(targets, cancel);
        return Optional.of(attemptOf(cancel, id, userId));
    }

    // 결과를 모르는 취소를 처음과 같은 멱등키·금액·사유로 다시 보낸다. 토스가 이미 처리했으면 첫 응답을 그대로 돌려줘
    // 두 번 환불되지 않는다. 취소 기한은 다시 보지 않는다. 기한 안에 요청한 취소의 결과를 맞추는 것이다.
    @Transactional
    public ReservationCancelAttempt startCancelRetry(Long id, Long userId, Long paymentCancelId) {
        findOwnedForUpdate(id, userId);
        PaymentCancel cancel = paymentCancelRepository.findByIdAndPaymentReservationId(paymentCancelId, id)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_CANCEL_NOT_FOUND));
        cancel.verifyRetryable(LocalDateTime.now(clock));
        return attemptOf(cancel, id, userId);
    }

    // 취소 T2 (성공). 토스는 이미 환불했다. 여기서 실패하면 취소가 IN_PROGRESS로 남아 같은 취소의 재시도로 맞춘다.
    // 재시도가 겹쳐 이미 반영된 취소면 바꾸지 않고 현재 상태만 돌려준다.
    @Transactional
    public ReservationResponse completeCancel(ReservationCancelAttempt attempt, TossPayment.Cancel canceled,
                                              int tossBalanceAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = findOwnedForUpdate(attempt.reservationId(), attempt.userId());
        PaymentCancel cancel = findCancel(attempt.paymentCancelId());

        if (!cancel.isDone()) {
            cancel.complete(canceled.transactionKey(), canceled.canceledAtIn(clock.getZone(), now));
            reservation.completeRefund(cancel.getId(), now);
            int ourBalance = cancel.getPayment().getBalanceAmount();
            if (ourBalance != tossBalanceAmount) {
                // 환불은 이미 끝났다. 실패로 돌리지 않고, 대조할 수 있게 남긴다.
                log.error("[Payment] 취소 후 잔액 불일치 orderId={} ours={} toss={}",
                        cancel.getPayment().getOrderId(), ourBalance, tossBalanceAmount);
            }
        }
        return ReservationResponse.from(reservation, now);
    }

    // 취소 T2 (거절). 좌석과 결제는 그대로다. 묶어 둔 좌석을 풀어 다시 취소를 요청할 수 있게 한다.
    @Transactional
    public void failCancel(ReservationCancelAttempt attempt, String failureCode, String failureMessage) {
        Reservation reservation = findOwnedForUpdate(attempt.reservationId(), attempt.userId());
        PaymentCancel cancel = findCancel(attempt.paymentCancelId());

        cancel.fail(failureCode, failureMessage);
        reservation.withdrawRefund(cancel.getId());
    }

    // 승인 확정은 상태를 바꾸고 응답까지 만든다. 응답까지 한 쿼리로 만들려고 영화·지점을 함께 가져온다.
    private Reservation findOwnedWithDetails(Long id, Long userId) {
        return reservationRepository.findOwnedWithDetails(id, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    // 남의 예매도 RESERVATION_NOT_FOUND다. 403이나 별도 코드를 주면 순차 id를 넣어 보며
    // 어떤 예매가 존재하는지 알아낼 수 있다. 조회 단계에서 걸러지므로 상태 변경 메서드에 닿지 않는다.
    private Reservation findOwnedForUpdate(Long id, Long userId) {
        return reservationRepository.findOwnedForUpdate(id, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentCancel findCancel(Long paymentCancelId) {
        return paymentCancelRepository.findById(paymentCancelId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_CANCEL_NOT_FOUND));
    }

    private ReservationCancelAttempt attemptOf(PaymentCancel cancel, Long reservationId, Long userId) {
        return new ReservationCancelAttempt(cancel.getId(), reservationId, userId,
                cancel.getPayment().getPaymentKey(), cancel.getIdempotencyKey(), cancel.getAmount(), cancel.getReason());
    }

    // 토스 결제 내역에 남는 사유다. 회원이 쓴 문구를 그대로 토스에 남기지 않도록 서버가 만든다.
    private String cancelReasonOf(List<ReservationSeat> seats, boolean all) {
        String labels = seats.stream()
                .sorted(Comparator.comparingInt(ReservationSeat::getRowNum).thenComparingInt(ReservationSeat::getColNum))
                .map(ReservationSeat::getLabel)
                .collect(Collectors.joining(", "));
        return (all ? "예매 전체 취소: " : "예매 부분 취소: ") + labels;
    }

    private void ensureNoPaymentInProgress(Long reservationId) {
        if (paymentRepository.existsByReservationIdAndStatus(reservationId, PaymentStatus.IN_PROGRESS)) {
            throw new CustomException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
    }

    // 결제위젯과 토스 결제 내역에 보이는 이름이다.
    private String orderNameOf(Reservation reservation) {
        return reservation.getScreening().getMovie().getTitle() + " " + reservation.getSeats().size() + "매";
    }

    // 중복은 좌석 키로 비교하므로 범위 검사가 먼저다. 키는 열이 범위 안일 때만 좌표마다 유일하다.
    private void validateSeats(TheaterType type, List<SeatRequest> seats) {
        if (seats.stream().anyMatch(s -> !type.isValidSeat(s.rowNum(), s.colNum()))) {
            throw new CustomException(ErrorCode.SEAT_OUT_OF_RANGE);
        }
        if (seats.stream().map(SeatRequest::key).distinct().count() != seats.size()) {
            throw new CustomException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        }
    }

    // 벌크 UPDATE라 INSERT보다 먼저 DB에 반영된다. 엔티티로 바꿔 한 번에 flush하면 Hibernate가 INSERT를 앞세운다.
    // 같은 만료 선점을 다른 요청이 먼저 풀었으면 예매 UPDATE가 0행이 되고 좌석도 건드리지 않는다.
    // 예매와 좌석 중 한쪽만 이 트랜잭션의 새 버전이 되는 일을 막기 위해서다(expireIfPending 주석).
    // 유니크 인덱스는 만료 시각을 모르므로, 행을 놓아주지 않으면 시간이 지난 좌석도 다시 선택할 수 없다.
    private void releaseExpiredHolds(Long screeningId, List<SeatRequest> seats, LocalDateTime now) {
        List<Integer> seatKeys = seats.stream().map(SeatRequest::key).toList();
        List<Long> expiredIds = reservationRepository.findExpiredHoldsBlocking(
                screeningId, seatKeys, ReservationStatus.PENDING, now);
        for (Long id : expiredIds) {
            if (reservationRepository.expireIfPending(id, ReservationStatus.PENDING, ReservationStatus.EXPIRED, now) == 1) {
                reservationSeatRepository.releaseOccupied(id, now);
            }
        }
    }

    // 경쟁이 없는 경우에 친절한 응답을 주기 위한 사전 검사다. 검사와 INSERT 사이의 틈은 saveHold의 유니크 제약이 막는다.
    private void ensureNotOccupied(Long screeningId, List<SeatRequest> seats, LocalDateTime now) {
        Set<Integer> taken = reservationSeatRepository
                .findOccupiedPositionsByScreeningId(screeningId, ReservationStatus.PENDING, now).stream()
                .map(p -> ReservationSeat.key(p.getRowNum(), p.getColNum()))
                .collect(Collectors.toSet());
        if (seats.stream().map(SeatRequest::key).anyMatch(taken::contains)) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
        }
    }

    // 동시 요청이 같은 좌석을 먼저 INSERT한 경우는 유니크 제약 위반으로 여기서 잡힌다.
    private Reservation saveHold(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
        } catch (ConcurrencyFailureException e) {
            // 데드락·락 대기 타임아웃. 좌석이 팔렸다는 뜻이 아니라 판정하지 못했다는 뜻이라
            // 이미 선택된 좌석과 구분해서 재시도를 안내한다.
            throw new CustomException(ErrorCode.SEAT_RESERVATION_CONFLICT);
        }
    }

    private List<SeatRequest> orderedSeats(List<SeatRequest> seats) {
        return seats.stream()
                .sorted(Comparator.comparingInt(SeatRequest::rowNum)
                        .thenComparingInt(SeatRequest::colNum))
                .toList();
    }
}
