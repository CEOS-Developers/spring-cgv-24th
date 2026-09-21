package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.domain.theater.enums.TheaterRegion;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "theaters")
public class Theater extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 30)
    private TheaterRegion region;

    @Builder
    private Theater(
            String name,
            String address,
            TheaterRegion region
    ) {
        this.name = name;
        this.address = address;
        this.region = region;
    }

    public void update(
            String name,
            String address,
            TheaterRegion region
    ) {
        this.name = name;
        this.address = address;
        this.region = region;
    }
}