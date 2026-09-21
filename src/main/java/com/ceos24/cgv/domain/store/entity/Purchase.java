package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "purchase")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    public static Purchase create(User user, Cinema cinema) {
        if (user == null || cinema == null) {
            throw new IllegalArgumentException("사용자와 영화관은 필수입니다.");
        }

        Purchase purchase = new Purchase();
        purchase.user = user;
        purchase.cinema = cinema;
        purchase.purchasedAt = LocalDateTime.now();
        return purchase;
    }
}
