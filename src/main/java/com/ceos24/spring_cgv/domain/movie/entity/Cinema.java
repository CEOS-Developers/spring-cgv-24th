package com.ceos24.spring_cgv.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "cinema",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cinema_region_address",
                columnNames = {"region", "address"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "region", length = 50, nullable = false)
    private String region;

    @Column(name = "address", length = 200, nullable = false)
    private String address;

    @Builder
    private Cinema(String name, String region, String address) {
        this.name = name;
        this.region = region;
        this.address = address;
    }

    public void update(String name, String region, String address) {
        this.name = name;
        this.region = region;
        this.address = address;
    }
}
