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

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CinemaLikeService {
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaLikeRepository cinemaLikeRepository;

    @Transactional
    public boolean toggle(Long userId, Long cinemaId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        Optional<CinemaLike> existingLike = cinemaLikeRepository.findByUser_IdAndCinema_Id(userId, cinemaId);
        if (existingLike.isPresent()) {
            cinemaLikeRepository.delete(existingLike.get());
            return false;
        }

        cinemaLikeRepository.save(CinemaLike.builder()
                .user(user)
                .cinema(cinema)
                .build());
        return true;
    }
}
