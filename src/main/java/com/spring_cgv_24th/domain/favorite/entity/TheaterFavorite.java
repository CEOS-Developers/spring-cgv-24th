package com.spring_cgv_24th.domain.favorite.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "theater_favorite",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"member_id", "theater_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TheaterFavorite extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_favorite_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_theater_favorite_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false, foreignKey = @ForeignKey(name = "fk_theater_favorite_theater"))
    private Theater theater;

    @Builder
    public TheaterFavorite(Member member, Theater theater) {
        this.member = member;
        this.theater = theater;
    }
}
