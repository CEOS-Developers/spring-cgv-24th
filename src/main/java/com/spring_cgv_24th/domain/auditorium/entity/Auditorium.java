package com.spring_cgv_24th.domain.auditorium.entity;

import com.spring_cgv_24th.domain.theater.entity.Theater;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auditorium")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auditorium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auditorium_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auditorium_theater"))
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auditorium_type"))
    private AuditoriumType type;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Builder
    public Auditorium(Theater theater, AuditoriumType type, String name) {
        this.theater = theater;
        this.type = type;
        this.name = name;
    }
}
