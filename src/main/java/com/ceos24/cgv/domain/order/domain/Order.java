package com.ceos24.cgv.domain.order.domain;

import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.store.domain.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order {

    public Order(Member member, Store store, Long totalPrice) {
        this.member = member;
        this.store = store;
        this.totalPrice = totalPrice;
        this.createdAt = LocalDateTime.now();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Store store;

    private Long totalPrice;

    private LocalDateTime createdAt;
}
