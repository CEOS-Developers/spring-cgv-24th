package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.entity.FavoriteMovie;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.FavoriteMovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
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

        Movie movie =
                movieRepository.findById(movieId).orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (favoriteMovieRepository.existsByMovieIdAndMemberId(movieId, member.getId())) {
            throw new BusinessException(ErrorCode.FAVORITE_MOVIE_ALREADY_EXIST);
        }

        favoriteMovieRepository.save(new FavoriteMovie(movie, member));
    }

    @Transactional
    public void deleteFavoriteMovie(Long favoriteMovieId, Long memberId) {

        FavoriteMovie favoriteMovie = favoriteMovieRepository
                .findById(favoriteMovieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_MOVIE_NOT_FOUND));
        memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!favoriteMovie.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }

        favoriteMovieRepository.delete(favoriteMovie);
    }

    @Transactional(readOnly = true)
    public GetMovieResponse getAllFavoriteMovies(Long memberId) {

        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return new GetMovieResponse(favoriteMovieRepository.findAllByMemberId(member.getId()).stream()
                .map(FavoriteMovie::getMovie)
                .map(MovieInfo::from)
                .toList());
    }
}
