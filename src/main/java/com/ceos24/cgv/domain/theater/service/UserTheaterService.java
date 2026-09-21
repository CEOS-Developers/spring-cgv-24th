package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.entity.UserTheater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorCode;
import com.ceos24.cgv.domain.theater.exception.TheaterException;
import com.ceos24.cgv.domain.theater.exception.UserTheaterErrorCode;
import com.ceos24.cgv.domain.theater.exception.UserTheaterException;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.theater.repository.UserTheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorCode;
import com.ceos24.cgv.domain.user.exception.UserException;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserTheaterService {

    private final UserTheaterRepository userTheaterRepository;
    private final UserRepository userRepository;
    private final TheaterRepository theaterRepository;

    /**
     * 영화관 찜 등록
     */
    @Transactional
    public void likeTheater(
            Long userId,
            Long theaterId
    ) {
        User user = getUser(userId);
        Theater theater = getTheater(theaterId);

        boolean alreadyLiked =
                userTheaterRepository
                        .existsByUser_IdAndTheater_Id(
                                userId,
                                theaterId
                        );

        if (alreadyLiked) {
            throw new UserTheaterException(
                    UserTheaterErrorCode.THEATER_ALREADY_LIKED
            );
        }

        UserTheater userTheater = UserTheater.builder()
                .user(user)
                .theater(theater)
                .build();

        userTheaterRepository.save(userTheater);
    }

    /**
     * 영화관 찜 취소
     */
    @Transactional
    public void unlikeTheater(
            Long userId,
            Long theaterId
    ) {
        UserTheater userTheater =
                userTheaterRepository
                        .findByUser_IdAndTheater_Id(
                                userId,
                                theaterId
                        )
                        .orElseThrow(() ->
                                new UserTheaterException(
                                        UserTheaterErrorCode
                                                .THEATER_LIKE_NOT_FOUND
                                )
                        );

        userTheaterRepository.delete(userTheater);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private Theater getTheater(Long theaterId) {
        return theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new TheaterException(
                                TheaterErrorCode.THEATER_NOT_FOUND
                        )
                );
    }
}