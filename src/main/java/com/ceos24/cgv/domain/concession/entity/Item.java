package com.ceos24.cgv.domain.concession.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    private Item(String name, Integer price) {
        this.name = name;
        this.price = price;
    }

    public static Item create(String name, Integer price) {
        return new Item(name, price);
    }
}
