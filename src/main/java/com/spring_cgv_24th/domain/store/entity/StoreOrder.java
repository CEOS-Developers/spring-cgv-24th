package com.spring_cgv_24th.domain.store.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_store_order_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false, foreignKey = @ForeignKey(name = "fk_store_order_theater"))
    private Theater theater;

    @Column(name = "purchased_at", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime purchasedAt;

    @Builder
    public StoreOrder(Member member, Theater theater) {
        this.member = member;
        this.theater = theater;
        this.purchasedAt = LocalDateTime.now();
    }

}
