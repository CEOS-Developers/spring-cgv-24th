package com.ceos24.cgv.domain.cinema.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
// 테이블 유니크 제약조건 추가 todo: 학습필요
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_auditorium_cinema_name",
                        columnNames = {"cinema_id", "name"}
                )
        }
)
public class Auditorium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auditorium_type_id", nullable = false)
    private AuditoriumType auditoriumType;

    @Column(length = 50, nullable = false)
    private String name;


    public static Auditorium create(
            Cinema cinema,
            AuditoriumType auditoriumType,
            String name
    ) {
        Auditorium auditorium = new Auditorium();
        auditorium.cinema = cinema;
        auditorium.auditoriumType = auditoriumType;
        auditorium.name = name;

        return auditorium;
    }
}
