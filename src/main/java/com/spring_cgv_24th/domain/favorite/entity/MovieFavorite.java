package com.spring_cgv_24th.domain.favorite.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "movie_favorite",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"member_id", "movie_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieFavorite extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_favorite_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_movie_favorite_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, foreignKey = @ForeignKey(name = "fk_movie_favorite_movie"))
    private Movie movie;

    @Builder
    public MovieFavorite(Member member, Movie movie) {
        this.member = member;
        this.movie = movie;
    }
}
