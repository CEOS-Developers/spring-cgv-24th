package com.ceos24.cgv.domain.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(columnDefinition = "TEXT")
    private String description;

    public static Product create(
            String name,
            int price,
            String description
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품 이름은 필수입니다.");
        }
        if (price < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        }

        Product product = new Product();
        product.name = name;
        product.price = price;
        product.description = description;
        return product;
    }
}
