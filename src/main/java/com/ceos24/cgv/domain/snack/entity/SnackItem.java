package com.ceos24.cgv.domain.snack.entity;

import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "snack_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_snack_item_name",
                        columnNames = "name"
                )
        }
)
public class SnackItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snack_item_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 현재 판매 가격
     */
    @Column(name = "price", nullable = false)
    private Integer price;

    @Builder
    private SnackItem(
            String name,
            Integer price
    ) {
        this.name = name;
        this.price = price;
    }

    public void update(
            String name,
            Integer price
    ) {
        this.name = name;
        this.price = price;
    }
}