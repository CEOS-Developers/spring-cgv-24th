package com.ceos24.cgv.domain.branch.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Theater extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "theater_type", nullable = false, length = 20)
    private TheaterType theaterType;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder
    private Theater(Branch branch, TheaterType theaterType, String name) {
        this.branch = branch;
        this.theaterType = theaterType;
        this.name = name;
    }
}
