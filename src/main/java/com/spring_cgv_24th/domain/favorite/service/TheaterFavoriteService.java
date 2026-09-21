package com.spring_cgv_24th.domain.favorite.service;

import com.spring_cgv_24th.domain.favorite.dto.TheaterFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.entity.TheaterFavorite;
import com.spring_cgv_24th.domain.favorite.repository.TheaterFavoriteRepository;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.theater.dto.TheaterResDTO;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterFavoriteService {

    private final MemberRepository memberRepository;
    private final TheaterRepository theaterRepository;
    private final TheaterFavoriteRepository theaterFavoriteRepository;

    // 임시: 로그인 구현 후에는 memberId 파라미터 제거하고 인증된 회원 ID 사용할 것임.
    @Transactional
    public TheaterFavoriteResDTO addFavorite(Long theaterId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));
        if (theaterFavoriteRepository.existsByMember_IdAndTheater_Id(memberId, theaterId)) {
            throw new CustomException(ErrorCode.THEATER_FAVORITE_ALREADY_EXISTS);
        }

        TheaterFavorite favorite = theaterFavoriteRepository.save(
                TheaterFavorite.builder().member(member).theater(theater).build());
        return TheaterFavoriteResDTO.from(favorite);
    }

    // 임시: 로그인 구현 후에는 memberId 파라미터 제거하고 인증된 회원 ID 사용할 것임.
    @Transactional
    public void removeFavorite(Long theaterId, Long memberId) {
        TheaterFavorite favorite = theaterFavoriteRepository.findByMember_IdAndTheater_Id(memberId, theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_FAVORITE_NOT_FOUND));
        theaterFavoriteRepository.delete(favorite);
    }

    public List<TheaterResDTO> getFavorites(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return theaterFavoriteRepository.findAllByMember_IdOrderByIdDesc(memberId).stream()
                .map(favorite -> TheaterResDTO.from(favorite.getTheater()))
                .toList();
    }
}
