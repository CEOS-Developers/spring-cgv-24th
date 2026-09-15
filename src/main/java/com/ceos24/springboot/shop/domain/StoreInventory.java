package com.ceos24.springboot.shop.domain;

import com.ceos24.springboot.theater.domain.Theater;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@Table(name = "store_inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreInventory {
//  영화관 당 매점의 재고 수량을 나타냄

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long InventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "stock", nullable = false)
    private Integer stock;
}