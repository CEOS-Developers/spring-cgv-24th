package com.ceos24.cgv.domain.theater.domain;

import com.ceos24.cgv.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"theater_id", "member_id"}))
@Entity
public class FavoriteTheater {

    public FavoriteTheater(Theater theater, Member member) {
        this.theater = theater;
        this.member = member;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false, name = "theater_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Theater theater;

    @JoinColumn(nullable = false, name = "member_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;
}
