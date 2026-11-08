package com.cgvclone.cgv.domain.stock;

import com.cgvclone.cgv.domain.cinema.Cinema;
import com.cgvclone.cgv.domain.goods.Goods;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_cinema_goods", columnNames = {"cinema_id", "goods_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(nullable = false)
    private Integer quantity;

    public void increase(int quantity) {
        validateQuantity(quantity);
        this.quantity = Math.addExact(this.quantity, quantity);
    }

    public void decrease(int quantity) {
        validateQuantity(quantity);
        if (this.quantity < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.quantity -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("변경 수량은 1 이상이어야 합니다.");
        }
    }
}
