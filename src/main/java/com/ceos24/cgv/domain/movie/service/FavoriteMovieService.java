package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.movie.domain.FavoriteMovie;
import com.ceos24.cgv.domain.movie.domain.Movie;
import com.ceos24.cgv.domain.movie.repository.FavoriteMovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FavoriteMovieService {

    private final MovieRepository movieRepository;
    private final MemberRepository memberRepository;
    private final FavoriteMovieRepository favoriteMovieRepository;

    @Transactional
    public void addFavoriteMovie(Long movieId, Long memberId) {

        Movie movie = movieRepository.findById(movieId).orElseThrow();
        Member member = memberRepository.findById(memberId).orElseThrow();

        favoriteMovieRepository.save(new FavoriteMovie(movie, member));
    }
}
