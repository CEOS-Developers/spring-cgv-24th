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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CinemaLikeService {
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaLikeRepository cinemaLikeRepository;

    // 같은 회원의 동시 토글(더블 클릭, 재시도)을 회원 행 잠금으로 직렬화한다.
    // 직렬화하지 않으면 두 요청이 모두 '찜 없음'으로 읽고 삽입해 유니크 제약 위반(500)이 난다.
    // 잠금 뒤의 조회가 앞선 트랜잭션의 커밋 결과를 보도록 READ_COMMITTED를 쓴다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean toggle(Long userId, Long cinemaId) {
        User user = userRepository.findByIdForUpdate(userId)
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
