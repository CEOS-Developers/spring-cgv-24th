package com.ceos24.spring_cgv.domain.like.service;

import com.ceos24.spring_cgv.domain.like.dto.response.CinemaLikeResponse;
import com.ceos24.spring_cgv.domain.like.entity.CinemaLike;
import com.ceos24.spring_cgv.domain.like.exception.CinemaLikeException;
import com.ceos24.spring_cgv.domain.like.exception.code.CinemaLikeErrorCode;
import com.ceos24.spring_cgv.domain.like.repository.CinemaLikeRepository;
import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.member.exception.MemberException;
import com.ceos24.spring_cgv.domain.member.exception.code.MemberErrorCode;
import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import com.ceos24.spring_cgv.domain.movie.exception.CinemaException;
import com.ceos24.spring_cgv.domain.movie.exception.code.CinemaErrorCode;
import com.ceos24.spring_cgv.domain.movie.repository.CinemaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaLikeService {

    private final CinemaLikeRepository cinemaLikeRepository;
    private final CinemaRepository cinemaRepository;
    private final MemberRepository memberRepository;

    /***
     * 함수 기능: 해당 영화관을 회원의 찜 목록에 추가합니다.
     * @param memberId 찜하는 회원 ID (인증 토큰에서 추출)
     * @param cinemaId 찜할 영화관 ID
     * @return CinemaLikeResponse 등록된 찜 ID, 영화관 정보, 찜한 시각
     */
    @Transactional
    public CinemaLikeResponse createCinemaLike(Long memberId, Long cinemaId) {

        // 이미 찜한 영화관인지 검사한다.
        if (cinemaLikeRepository.existsByMemberIdAndCinemaId(memberId, cinemaId)){
            throw new CinemaLikeException(CinemaLikeErrorCode.CINEMA_LIKE_ALREADY_EXISTS);
        }

        // 영화관을 조회한다.
        Cinema cinema = findCinemaById(cinemaId);

        // 회원을 조회한다.
        Member member = findMemberById(memberId);

        // CinemaLike를 생성해 저장한다.
        try {
            CinemaLike cinemaLike = cinemaLikeRepository.save(CinemaLike.builder()
                    .member(member)
                    .cinema(cinema)
                    .build());

            return CinemaLikeResponse.from(cinemaLike);

        } catch (DataIntegrityViolationException e){
            throw new CinemaLikeException(CinemaLikeErrorCode.CINEMA_LIKE_ALREADY_EXISTS);
        }
    }

    /***
     * 함수 기능: 해당 영화관을 회원의 찜 목록에서 제거합니다.
     * @param memberId 찜을 취소하는 회원 ID (인증 토큰에서 추출)
     * @param cinemaId 찜을 취소할 영화관 ID
     */
    @Transactional
    public void deleteCinemaLike(Long memberId, Long cinemaId) {

        // 해당 회원의 해당 영화관 찜을 조회한다.
        CinemaLike cinemaLike = findCinemaLike(memberId, cinemaId);

        // 조회한 찜을 삭제한다.
        cinemaLikeRepository.delete(cinemaLike);
    }

    /***
     * 함수 기능: 회원이 찜한 영화관 목록을 최근에 찜한 순서로 조회합니다.
     * @param memberId 조회할 회원 ID (인증 토큰에서 추출)
     * @return List<CinemaLikeResponse> 찜한 영화관 목록
     */
    public List<CinemaLikeResponse> findMyCinemaLikes(Long memberId) {

        // 해당 회원의 모든 영화관 찜 목록을 조회한다.
        List<CinemaLike> cinemaLikeList = cinemaLikeRepository.findAllByMemberIdOrderByLikedAtDesc(memberId);

        return cinemaLikeList.stream()
                .map(CinemaLikeResponse::from)
                .toList();
    }

    private @NonNull Cinema findCinemaById(Long cinemaId) {
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new CinemaException(CinemaErrorCode.CINEMA_NOT_FOUND));
    }

    private @NonNull Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private @NonNull CinemaLike findCinemaLike(Long memberId, Long cinemaId) {
        return cinemaLikeRepository.findByMemberIdAndCinemaId(memberId, cinemaId)
                .orElseThrow(() -> new CinemaLikeException(CinemaLikeErrorCode.CINEMA_LIKE_NOT_FOUND));
    }
}