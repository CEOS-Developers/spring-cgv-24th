package com.ceos24.cgv.domain.concession.entity;

import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @Column(nullable = false)
    private Instant orderedAt;

    @Column(nullable = false)
    private Integer totalPrice;

    private Order(User user, Theater theater, Instant orderedAt, Integer totalPrice) {
        this.user = user;
        this.theater = theater;
        this.orderedAt = orderedAt;
        this.totalPrice = totalPrice;
    }

    public static Order create(User user, Theater theater, Integer totalPrice) {
        return new Order(user, theater, Instant.now(), totalPrice);
    }
}
