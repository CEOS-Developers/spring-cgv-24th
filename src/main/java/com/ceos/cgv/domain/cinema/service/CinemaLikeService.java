package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.entity.CinemaLike;
import com.ceos.cgv.domain.cinema.repository.CinemaLikeRepository;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CinemaLikeService {
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaLikeRepository cinemaLikeRepository;

    @Transactional
    public void create(Long userId, Long cinemaId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
        if (cinemaLikeRepository.existsByUser_IdAndCinema_Id(userId, cinemaId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LIKE);
        }
        cinemaLikeRepository.save(CinemaLike.builder()
                .user(user)
                .cinema(cinema)
                .build());
    }

    @Transactional
    public void delete(Long userId, Long cinemaId) {
        CinemaLike cinemaLike = cinemaLikeRepository.findByUser_IdAndCinema_Id(userId, cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIKE_NOT_FOUND));
        cinemaLikeRepository.delete(cinemaLike);
    }
}
