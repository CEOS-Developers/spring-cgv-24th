package com.cgvclone.cgv.domain.showtime;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;

    @Transactional(readOnly = true)
    public Showtime getShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new GlobalException(ErrorCode.SHOWTIME_NOT_FOUND));
    }
}
