package com.ceos24.cgv.domain;

import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime reservedAt;

    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationSeat> seats = new ArrayList<>();

    @Builder
    private Reservation(Member member, Screening screening) {
        this.member = member;
        this.screening = screening;
        this.status = ReservationStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    // screening을 부모 값에서 가져와 부모-자식 불일치를 구조적으로 차단한다.
    public void addSeat(int rowNum, int colNum, int paidPrice) {
        ReservationSeat seat = ReservationSeat.builder()
                .reservation(this)
                .screening(this.screening)
                .rowNum(rowNum)
                .colNum(colNum)
                .paidPrice(paidPrice)
                .build();
        this.seats.add(seat);
    }

    // orphanRemoval에 의해 좌석 행이 삭제된다.
    // 유니크 제약이 걸려 있어 행이 남으면 해당 좌석을 재판매할 수 없기 때문이다.
    public void cancel() {
        if (this.status == ReservationStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELLED);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.seats.clear();
    }

    public int getTotalPrice() {
        return seats.stream()
                .mapToInt(ReservationSeat::getPaidPrice)
                .sum();
    }
}
