package com.ceos24.springboot.favorite.service;

import com.ceos24.springboot.favorite.domain.TheaterFavorites;
import com.ceos24.springboot.favorite.dto.TheaterFavoriteResponse;
import com.ceos24.springboot.favorite.repository.TheaterFavoritesRepository;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterFavoriteService {

    private final TheaterFavoritesRepository theaterFavoritesRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;


    // 영화관 찜 등록
    @Transactional
    public TheaterFavoriteResponse addTheaterFavorite(
            Long userId,
            Long theaterId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다."
                        )
                );

        // 중복 찜 방지
        if (theaterFavoritesRepository
                .existsByUserAndTheater(user, theater)) {

            throw new IllegalArgumentException(
                    "이미 찜한 영화관입니다."
            );
        }

        TheaterFavorites favorite =
                TheaterFavorites.builder()
                        .user(user)
                        .theater(theater)
                        .build();

        TheaterFavorites savedFavorite =
                theaterFavoritesRepository.save(favorite);

        return TheaterFavoriteResponse.from(savedFavorite);
    }


    // 내가 찜한 영화관 목록
    public List<TheaterFavoriteResponse> getTheaterFavorites(
            Long userId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        return theaterFavoritesRepository.findAllByUser(user)
                .stream()
                .map(TheaterFavoriteResponse::from)
                .toList();
    }


    // 영화관 찜 해제
    @Transactional
    public void deleteTheaterFavorite(
            Long userId,
            Long theaterId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다."
                        )
                );

        TheaterFavorites favorite =
                theaterFavoritesRepository
                        .findByUserAndTheater(user, theater)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "찜하지 않은 영화관입니다."
                                )
                        );

        theaterFavoritesRepository.delete(favorite);
    }
}