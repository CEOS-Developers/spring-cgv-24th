package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Theater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String region;

    private String address;

    @Lob
    private String description;

    private String theaterImageUrl;

    private Theater(String name, String region, String address, String description, String theaterImageUrl) {
        this.name = name;
        this.region = region;
        this.address = address;
        this.description = description;
        this.theaterImageUrl = theaterImageUrl;
    }

    public static Theater create(String name, String region, String address, String description, String theaterImageUrl) {
        return new Theater(name, region, address, description, theaterImageUrl);
    }
}
