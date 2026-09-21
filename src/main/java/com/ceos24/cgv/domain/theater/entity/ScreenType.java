package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "screen_types")
public class ScreenType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screen_type_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 상영관 타입별 기본 가격
     */
    @Column(name = "base_price", nullable = false)
    private Integer basePrice;

    @Builder
    private ScreenType(
            String name,
            Integer basePrice
    ) {
        this.name = name;
        this.basePrice = basePrice;
    }

    public void update(
            String name,
            Integer basePrice
    ) {
        this.name = name;
        this.basePrice = basePrice;
    }
}