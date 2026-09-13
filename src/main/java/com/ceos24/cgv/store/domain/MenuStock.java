package com.ceos24.cgv.store.domain;

import jakarta.persistence.*;

@Entity
public class MenuStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Menu menu;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Store store;

    private Long stock;
}
