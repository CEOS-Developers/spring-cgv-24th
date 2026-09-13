package com.ceos24.cgv.theater.domain;

import com.ceos24.cgv.user.domain.User;
import jakarta.persistence.*;

@Entity
public class FavoriteTheater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Theater theater;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
