package com.ceos24.cgv.domain.cinema.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
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

    public static Cinema create(String name, String address, String region) {
        Cinema cinema = new Cinema();
        cinema.name = name;
        cinema.address = address;
        cinema.region = region;
        return cinema;
    }
}
