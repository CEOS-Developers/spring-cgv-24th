package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.theater.domain.FavoriteTheater;
import com.ceos24.cgv.domain.theater.domain.Theater;
import com.ceos24.cgv.domain.theater.repository.FavoriteTheaterRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
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
        Theater theater = theaterRepository.findById(theaterId).orElseThrow();
        Member member = memberRepository.findById(memberId).orElseThrow();
        FavoriteTheater favoriteTheater = new FavoriteTheater(theater, member);
        System.out.println("memberId : " + favoriteTheater.getMember().getId());
        System.out.println("theaterId : " + favoriteTheater.getTheater().getId());

        favoriteTheaterRepository.save(favoriteTheater);
    }
}
