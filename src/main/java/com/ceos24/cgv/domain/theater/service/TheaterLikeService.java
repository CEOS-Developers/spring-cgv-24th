package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.response.TheaterLikeResponse;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.entity.TheaterLike;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterLikeRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterLikeService {

    private final TheaterLikeRepository theaterLikeRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;

    @Transactional
    public TheaterLikeResponse like(Long userId, Long theaterId) {
        theaterLikeRepository.findByUserIdAndTheaterId(userId, theaterId)
                .ifPresent(like -> {
                    throw new GeneralException(TheaterErrorStatus.ALREADY_LIKED_THEATER);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));

        TheaterLike saved = theaterLikeRepository.save(TheaterLike.create(user, theater));
        return TheaterLikeResponse.from(saved);
    }

    @Transactional
    public void unlike(Long userId, Long theaterId) {
        TheaterLike theaterLike = theaterLikeRepository.findByUserIdAndTheaterId(userId, theaterId)
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_LIKE_NOT_FOUND));
        theaterLikeRepository.delete(theaterLike);
    }
}
