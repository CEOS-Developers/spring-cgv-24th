package com.ceos.cgv.domain.concession.entity;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
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
@Table(name = "inventories", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_cinema_product", columnNames = {"cinema_id", "product_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    public Inventory(Cinema cinema, Product product, Integer stockQuantity) {
        this.cinema = cinema;
        this.product = product;
        this.stockQuantity = stockQuantity;
    }

    public void decrease(Integer quantity) {
        if (stockQuantity < quantity) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        stockQuantity -= quantity;
    }
}
