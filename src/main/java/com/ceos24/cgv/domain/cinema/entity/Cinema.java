package com.ceos24.cgv.domain.cinema.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 255, nullable = false)
    private String address;

    @Column(length = 30, nullable = false)
    private String region;

    // soft-delete
    @Column(nullable = false)
    private boolean active;

    private LocalDateTime deletedAt;

    public static Cinema create(String name, String address, String region) {

        Cinema cinema = new Cinema();
        cinema.name = name;
        cinema.address = address;
        cinema.region = region;
        cinema.active=true;
        cinema.deletedAt = null;
        return cinema;
    }

    public void deactivate() {
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }
}
