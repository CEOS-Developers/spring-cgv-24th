package com.spring_cgv_24th.domain.reservation.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = true, foreignKey = @ForeignKey(name = "fk_reservation_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_screening"))
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'CONFIRMED'")
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "reserved_at", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    private LocalDateTime reservedAt = LocalDateTime.now();

    @Column(name = "cancelled_at", columnDefinition = "timestamp(6)")
    private LocalDateTime cancelledAt;

    @Builder
    public Reservation(Member member, Screening screening) {
        this.member = member;
        this.screening = screening;
    }

    public void cancel(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
        this.status = ReservationStatus.CANCELLED;
    }
}
