package com.spring_cgv_24th.domain.screening.entity;

import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "screening",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"auditorium_id", "starts_at"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, foreignKey = @ForeignKey(name = "fk_screening_movie"))
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auditorium_id", nullable = false, foreignKey = @ForeignKey(name = "fk_screening_auditorium"))
    private Auditorium auditorium;

    @Column(name = "starts_at", nullable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime endsAt;

    @Builder
    public Screening(Movie movie, Auditorium auditorium, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.movie = movie;
        this.auditorium = auditorium;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
