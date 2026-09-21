package com.ceos.cgv.domain.cinema.entity;

import com.ceos.cgv.domain.cinema.enums.ScreenType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "screens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screen_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_type", nullable = false)
    private ScreenType screenType;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "seats_per_row", nullable = false)
    private Integer seatsPerRow;

    @Builder
    public Screen(Cinema cinema, ScreenType screenType, Integer rowCount, Integer seatsPerRow) {
        this.cinema = cinema;
        this.screenType = screenType;
        this.rowCount = rowCount;
        this.seatsPerRow = seatsPerRow;
    }
}
