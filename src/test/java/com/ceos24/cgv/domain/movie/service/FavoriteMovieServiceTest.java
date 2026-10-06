package com.ceos24.cgv.domain.movie.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.movie.entity.FavoriteMovie;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.FavoriteMovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteMovieServiceTest {

    @InjectMocks
    private FavoriteMovieService favoriteMovieService;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FavoriteMovieRepository favoriteMovieRepository;

    @Test
    @DisplayName("관심 영화 추가 성공")
    void addFavoriteMovie_Success() {
        // given
        Long movieId = 1L;
        Long memberId = 1L;

        Movie movie = mock(Movie.class);
        Member member = mock(Member.class);

        given(movieRepository.findById(movieId)).willReturn(Optional.of(movie));
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        // when
        favoriteMovieService.addFavoriteMovie(movieId, memberId);

        // then
        verify(favoriteMovieRepository).save(any(FavoriteMovie.class));
    }

    @Test
    @DisplayName("관심 영화 추가 실패 - 영화 없음")
    void addFavoriteMovie_Fail_MovieNotFound() {
        // given
        Long movieId = 1L;
        Long memberId = 1L;

        given(movieRepository.findById(movieId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> favoriteMovieService.addFavoriteMovie(movieId, memberId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MOVIE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("관심 영화 추가 실패 - 회원 없음")
    void addFavoriteMovie_Fail_MemberNotFound() {
        // given
        Long movieId = 1L;
        Long memberId = 1L;

        Movie movie = mock(Movie.class);

        given(movieRepository.findById(movieId)).willReturn(Optional.of(movie));
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> favoriteMovieService.addFavoriteMovie(movieId, memberId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }
}
