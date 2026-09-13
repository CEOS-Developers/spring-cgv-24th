package com.ceos24.cgv.store.domain;

import com.ceos24.cgv.theater.domain.Theater;
import jakarta.persistence.*;

@Entity
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @OneToOne(fetch = FetchType.LAZY)
    private Theater theater;

    private String name;

}
