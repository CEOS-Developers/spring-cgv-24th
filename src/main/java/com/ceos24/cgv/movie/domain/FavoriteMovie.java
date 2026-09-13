package com.ceos24.cgv.movie.domain;

import com.ceos24.cgv.user.domain.User;
import jakarta.persistence.*;

@Entity
public class FavoriteMovie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Movie movie;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
