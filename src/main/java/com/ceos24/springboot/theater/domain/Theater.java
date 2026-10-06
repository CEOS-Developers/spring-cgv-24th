package com.ceos24.springboot.theater.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "theater")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Theater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_id")
    private Long theaterId;

    @Column(name = "theater_name")
    private String theaterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "region")
    private Region region;

    // Theater 객체를 만듦.
    @Builder
    public Theater(
            String theaterName,
            Region region

    ) {
        this.theaterName = theaterName;
        this.region = region;

    }

//    Entity가 스스로 생성 책임을 가지도록 수정하였음.
    public static Theater create(String theaterName, Region region) {
        return Theater.builder()
                .theaterName(theaterName)
                .region(region)
                .build();
    }

}