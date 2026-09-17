package com.ceos24.cgv.domain.movie.domain;

import com.ceos24.cgv.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "member_id"}))
@Entity
public class FavoriteMovie {

    public FavoriteMovie(Movie movie, Member member) {
        this.movie = movie;
        this.member = member;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false, name = "movie_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Movie movie;

    @JoinColumn(nullable = false, name = "member_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;
}
