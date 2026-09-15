package com.ceos24.spring_cgv.domain.movie.entity;

import com.ceos24.spring_cgv.domain.movie.enums.ScreenType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "name", length = 20, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ScreenType type;

    @Column(name = "extra_charge", nullable = false)
    private Integer extraCharge;

    @Builder
    private Screen(Cinema cinema, String name, ScreenType type, Integer extraCharge) {
        this.cinema = cinema;
        this.name = name;
        this.type = type;
        this.extraCharge = extraCharge == null ? 0 : extraCharge;
    }
}
