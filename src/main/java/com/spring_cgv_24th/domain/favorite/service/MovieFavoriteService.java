package com.spring_cgv_24th.domain.favorite.service;

import com.spring_cgv_24th.domain.favorite.dto.MovieFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.entity.MovieFavorite;
import com.spring_cgv_24th.domain.favorite.repository.MovieFavoriteRepository;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieFavoriteService {

    private final MemberRepository memberRepository;
    private final MovieRepository movieRepository;
    private final MovieFavoriteRepository movieFavoriteRepository;

    // 임시: 로그인 구현 후에는 memberId 파라미터 제거하고 인증된 회원 ID 사용할 것임.
    @Transactional
    public MovieFavoriteResDTO addFavorite(Long movieId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new CustomException(ErrorCode.MOVIE_NOT_FOUND));
        if (movieFavoriteRepository.existsByMember_IdAndMovie_Id(memberId, movieId)) {
            throw new CustomException(ErrorCode.MOVIE_FAVORITE_ALREADY_EXISTS);
        }

        MovieFavorite favorite = movieFavoriteRepository.save(
                MovieFavorite.builder().member(member).movie(movie).build());
        return MovieFavoriteResDTO.from(favorite);
    }

    @Transactional
    public void removeFavorite(Long movieId, Long memberId) {
        MovieFavorite favorite = movieFavoriteRepository.findByMember_IdAndMovie_Id(memberId, movieId)
                .orElseThrow(() -> new CustomException(ErrorCode.MOVIE_FAVORITE_NOT_FOUND));
        movieFavoriteRepository.delete(favorite);
    }

    public List<MovieResDTO> getFavorites(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return movieFavoriteRepository.findAllByMember_IdOrderByIdDesc(memberId).stream()
                .map(favorite -> MovieResDTO.from(favorite.getMovie()))
                .toList();
    }
}
