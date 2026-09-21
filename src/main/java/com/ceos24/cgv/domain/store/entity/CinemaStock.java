package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Getter
@Table(
        name = "cinema_stock",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cinema_stock_cinema_product",
                        columnNames = {"cinema_id", "product_id"}
                )
        }
)
public class CinemaStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    public static CinemaStock create(
            Cinema cinema,
            Product product,
            int quantity
    ) {
        if (cinema == null || product == null) {
            throw new IllegalArgumentException("영화관과 상품은 필수입니다.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }

        CinemaStock cinemaStock = new CinemaStock();
        cinemaStock.cinema = cinema;
        cinemaStock.product = product;
        cinemaStock.quantity = quantity;
        return cinemaStock;
    }

    public void decrease(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("구매 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }

        this.quantity -= quantity;
    }
}
