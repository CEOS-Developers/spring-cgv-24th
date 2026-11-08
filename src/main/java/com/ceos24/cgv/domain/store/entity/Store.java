package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.theater.entity.Theater;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Store {

    public Store(Theater theater, String name) {
        this.theater = theater;
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    private Theater theater;

    @jakarta.persistence.Column(length = 50)
    private String name;
}
