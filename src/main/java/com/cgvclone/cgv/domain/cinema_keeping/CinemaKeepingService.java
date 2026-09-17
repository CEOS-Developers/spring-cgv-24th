package com.cgvclone.cgv.domain.cinema_keeping;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.User.User;
import com.cgvclone.cgv.domain.User.UserService;
import com.cgvclone.cgv.domain.cinema.Cinema;
import com.cgvclone.cgv.domain.cinema.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CinemaKeepingService {

    private final CinemaKeepingRepository cinemaKeepingRepository;
    private final UserService userService;
    private final CinemaService cinemaService;

    @Transactional(readOnly = true)
    public CinemaKeeping getCinemaKeeping(User user, Cinema cinema) {
        return cinemaKeepingRepository.findByUserAndCinema(user, cinema)
                .orElseThrow(() -> new GlobalException(ErrorCode.CINEMA_KEEPING_NOT_FOUND));
    }

    public void keepCinema(Long cinemaId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userService.getUser(currentUserId);

        Cinema cinema = cinemaService.getCinemaEntity(cinemaId);

        CinemaKeeping cinemaKeeping = CinemaKeeping.builder()
                .user(user)
                .cinema(cinema)
                .build();

        cinemaKeepingRepository.save(cinemaKeeping);
    }

    public void cancelCinemaKeeping(Long cinemaId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userService.getUser(currentUserId);

        Cinema cinema = cinemaService.getCinemaEntity(cinemaId);

        CinemaKeeping cinemaKeeping = getCinemaKeeping(user, cinema);

        cinemaKeepingRepository.delete(cinemaKeeping);
    }
}
