package com.ceos24.cgv.domain.concession.entity;

import com.ceos24.cgv.domain.theater.entity.Theater;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_theater_item",
                columnNames = {"theater_id", "item_id"}
        )
)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    private Stock(Theater theater, Item item, Integer quantity) {
        this.theater = theater;
        this.item = item;
        this.quantity = quantity;
    }

    public static Stock create(Theater theater, Item item, Integer quantity) {
        return new Stock(theater, item, quantity);
    }

    public void update(Integer quantity) {
        this.quantity = quantity;
    }
}
