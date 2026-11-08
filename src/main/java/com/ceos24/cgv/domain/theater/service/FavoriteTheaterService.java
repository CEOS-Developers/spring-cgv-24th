package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.theater.dto.TheaterInfo;
import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.entity.FavoriteTheater;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.FavoriteTheaterRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FavoriteTheaterService {

    private final TheaterRepository theaterRepository;
    private final MemberRepository memberRepository;
    private final FavoriteTheaterRepository favoriteTheaterRepository;

    @Transactional
    public void addFavoriteTheater(Long theaterId, Long memberId) {
        Theater theater = theaterRepository
                .findById(theaterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THEATER_NOT_FOUND));
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (favoriteTheaterRepository.existsByTheaterIdAndMemberId(theater.getId(), member.getId())) {
            throw new BusinessException(ErrorCode.FAVORITE_THEATER_ALREADY_EXIST);
        }
        FavoriteTheater favoriteTheater = new FavoriteTheater(theater, member);
        favoriteTheaterRepository.save(favoriteTheater);
    }

    @Transactional
    public void deleteFavoriteTheater(Long favoriteTheaterId, Long memberId) {
        FavoriteTheater favoriteTheater = favoriteTheaterRepository
                .findById(favoriteTheaterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_THEATER_NOT_FOUND));
        memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!favoriteTheater.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }

        favoriteTheaterRepository.delete(favoriteTheater);
    }

    @Transactional(readOnly = true)
    public GetTheaterResponse getAllFavoriteTheaters(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<TheaterInfo> theaters = favoriteTheaterRepository.findAllByMemberId(member.getId()).stream()
                .map(FavoriteTheater::getTheater)
                .map(TheaterInfo::from)
                .toList();

        return new GetTheaterResponse(theaters);
    }
}
