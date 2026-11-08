package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    private static final int HOLD_MINUTES = 10;
    private static final int CANCEL_DEADLINE_MINUTES = 20;
    // 토스 승인 호출의 최대 소요 시간(연결 5초 + 읽기 60초)보다 넉넉하게 둔다.
    private static final int PAYMENT_GRACE_MINUTES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    // 선점 만료 예정 시각. 확정 후에는 의미가 없지만, 조회 조건을 단순하게 두려고 비우지 않는다.
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationSeat> seats = new ArrayList<>();

    // 좌석을 고른 시각은 createdAt이 갖는다.
    @Builder
    private Reservation(User user, Screening screening, LocalDateTime now) {
        this.user = user;
        this.screening = screening;
        this.status = ReservationStatus.PENDING;
        this.expiresAt = now.plusMinutes(HOLD_MINUTES);
    }

    // screening을 부모 값에서 가져와 부모-자식 불일치를 구조적으로 차단한다.
    // 가격도 같은 이유로 호출자에게 받지 않는다. 받으면 회차 가격과 다른 값이 들어올 틈이 생긴다.
    public void addSeat(int rowNum, int colNum, AudienceType audienceType) {
        ReservationSeat seat = ReservationSeat.builder()
                .reservation(this)
                .screening(this.screening)
                .rowNum(rowNum)
                .colNum(colNum)
                .audienceType(audienceType)
                .paidPrice(audienceType.calculatePrice(this.screening.getPrice()))
                .build();
        this.seats.add(seat);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.status.isHoldExpired(this.expiresAt, now);
    }

    public void verifyPayable(LocalDateTime now) {
        requirePending();
        if (isExpired(now)) {
            throw new CustomException(ErrorCode.RESERVATION_EXPIRED);
        }
    }

    // 토스 승인 호출은 1분 넘게 걸릴 수 있다. 그사이 선점이 만료되면 다른 요청이 좌석을 가져가고
    // 토스는 그대로 승인해 이중 판매가 된다. 호출이 끝날 때까지 좌석이 풀리지 않게 만료 시각을 뒤로 민다.
    // 남은 시간이 유예보다 길면 그대로 둔다. 줄이면 결제를 시작했다는 이유로 선점이 짧아진다.
    public void holdForPayment(LocalDateTime now) {
        verifyPayable(now);
        LocalDateTime paymentDeadline = now.plusMinutes(PAYMENT_GRACE_MINUTES);
        if (paymentDeadline.isAfter(this.expiresAt)) {
            this.expiresAt = paymentDeadline;
        }
    }

    // 거절되면 늘린 시간을 결제 시작 전 값으로 되돌린다. 남겨 두면 거절을 반복해 좌석을 계속 붙잡을 수 있다.
    // 되돌린 값이 이미 지났으면 선점은 그대로 만료된 것으로 보인다.
    public void endPaymentHold(LocalDateTime expiresAtBeforePayment) {
        if (expiresAtBeforePayment.isBefore(this.expiresAt)) {
            this.expiresAt = expiresAtBeforePayment;
        }
    }

    public void confirm(LocalDateTime now) {
        verifyPayable(now);
        this.status = ReservationStatus.RESERVED;
        this.confirmedAt = now;
    }

    // 결과를 몰랐던 결제가 토스에서 승인된 것으로 확인됐을 때만 부른다. 만료 시각은 보지 않는다.
    // 만료된 선점의 좌석은 다른 요청이 PENDING → EXPIRED로 바꿀 때 함께 풀린다. 그래서 아직 PENDING이면 좌석은
    // 이 예매의 것이고, 회원은 이미 돈을 냈으므로 환불보다 확정이 맞다. 호출하는 쪽이 예매 행을 잠가 두므로
    // 확정하는 사이에 다른 요청이 선점을 만료시킬 수 없다.
    public void confirmAfterRecovery(LocalDateTime now) {
        requirePending();
        this.status = ReservationStatus.RESERVED;
        this.confirmedAt = now;
    }

    public boolean isPending() {
        return this.status == ReservationStatus.PENDING;
    }

    public boolean isScreeningStarted(LocalDateTime now) {
        return !now.isBefore(screening.getStartAt());
    }

    // 결과를 몰랐던 결제가 상영 시작 뒤에야 승인으로 확인됐을 때 부른다. 확정하면 볼 수 없는 회차에 돈만 받고
    // 취소 기한도 지나 회원이 환불받을 길이 없다. 좌석을 잃은 자동 환불과 같이 EXPIRED로 두고 좌석을 놓는다.
    // 벌크 expireIfPending은 만료 시각이 지난 선점만 바꾸는데, 이 경우는 늘린 선점 시간이 남아 있을 수 있다.
    public void expireAfterScreeningStarted(LocalDateTime now) {
        requirePending();
        this.status = ReservationStatus.EXPIRED;
        this.seats.forEach(seat -> seat.cancel(this.id, now));
    }

    // 결제 전 선점 취소와 결제된 예매의 환불 취소(전체·부분)가 같은 검사를 거친다.
    public void verifyCancellable(LocalDateTime now) {
        if (this.status == ReservationStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELLED);
        }
        if (this.status == ReservationStatus.EXPIRED) {
            throw new CustomException(ErrorCode.RESERVATION_EXPIRED);
        }
        // 취소 기한은 확정된 예매에만 적용한다. 선점은 아직 확정 전이라 언제든 놓을 수 있다.
        if (this.status == ReservationStatus.RESERVED && !isCancellableAt(now)) {
            throw new CustomException(ErrorCode.CANCEL_DEADLINE_PASSED);
        }
    }

    public boolean isReserved() {
        return this.status == ReservationStatus.RESERVED;
    }

    // 결제 전 선점 취소. 돌려줄 돈이 없어 바로 놓는다. 결제된 예매는 환불이 확인된 뒤 completeRefund로만 취소된다.
    public void cancel(LocalDateTime now) {
        verifyCancellable(now);
        requirePending();
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
        // 좌석 행은 남기고 점유만 푼다. 유니크 제약에서 빠지므로 같은 좌석을 다시 팔 수 있다.
        this.seats.forEach(seat -> seat.cancel(this.id, now));
    }

    // 환불할 좌석을 고른다. seatKeys가 null이면 남은 좌석 전부(전체 취소)다.
    // 좌석은 아직 놓지 않는다. 환불이 확인되기 전에 놓으면 거절됐을 때 되돌릴 수 없다.
    public List<ReservationSeat> selectSeatsToRefund(Collection<Integer> seatKeys, LocalDateTime now) {
        verifyCancellable(now);
        if (!isReserved()) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_RESERVED);
        }
        if (seatKeys == null) {
            return this.seats.stream().filter(ReservationSeat::isOccupied).toList();
        }
        Map<Integer, ReservationSeat> byKey = this.seats.stream()
                .collect(Collectors.toMap(seat -> ReservationSeat.key(seat.getRowNum(), seat.getColNum()),
                        Function.identity()));
        return seatKeys.stream()
                .map(key -> {
                    ReservationSeat seat = byKey.get(key);
                    if (seat == null) {
                        throw new CustomException(ErrorCode.SEAT_NOT_IN_RESERVATION);
                    }
                    if (!seat.isOccupied()) {
                        throw new CustomException(ErrorCode.SEAT_ALREADY_CANCELLED);
                    }
                    return seat;
                })
                .toList();
    }

    public void requestRefund(List<ReservationSeat> targets, PaymentCancel paymentCancel) {
        targets.forEach(seat -> seat.attachCancel(paymentCancel));
    }

    // 환불이 확인된 좌석을 놓는다. 남은 좌석이 없으면 예매 전체가 취소된 것이다.
    // 확정된 예매만 CANCELLED로 바꾼다. 선점이 만료되어 좌석을 잃은 뒤 자동 환불한 예매는 EXPIRED로 둔다.
    // 회원이 취소한 것이 아니어서, CANCELLED로 바꾸면 좌석이 왜 풀렸는지 되짚을 수 없다.
    public void completeRefund(Long paymentCancelId, LocalDateTime now) {
        this.seats.stream()
                .filter(seat -> seat.belongsTo(paymentCancelId) && seat.isOccupied())
                .forEach(seat -> seat.cancel(this.id, now));
        if (isReserved() && this.seats.stream().noneMatch(ReservationSeat::isOccupied)) {
            this.status = ReservationStatus.CANCELLED;
            this.cancelledAt = now;
        }
    }

    // 환불이 거절됐다. 좌석은 그대로 회원의 것이고, 다시 취소를 요청할 수 있다.
    public void withdrawRefund(Long paymentCancelId) {
        this.seats.stream()
                .filter(seat -> seat.belongsTo(paymentCancelId))
                .forEach(ReservationSeat::detachCancel);
    }

    // 결제 주문의 금액이다. 결제 전에는 취소된 좌석이 없어 모든 좌석의 합이다.
    public int getTotalPrice() {
        return seats.stream()
                .mapToInt(ReservationSeat::getPaidPrice)
                .sum();
    }

    private boolean isCancellableAt(LocalDateTime now) {
        return now.isBefore(screening.getStartAt().minusMinutes(CANCEL_DEADLINE_MINUTES));
    }

    private void requirePending() {
        if (this.status != ReservationStatus.PENDING) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_PENDING);
        }
    }
}
