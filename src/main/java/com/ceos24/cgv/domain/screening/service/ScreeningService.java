package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.screening.dto.ScreeningInfo;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.dto.ScreenInfo;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreeningService {

    private final ScreeningRepository screeningRepository;

    @Transactional(readOnly = true)
    public ScreeningInfo getScreeningInfo(Long screeningId) {
        Screening screening = screeningRepository
                .findById(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        Movie movie = screening.getMovie();
        Screen screen = screening.getScreen();
        return new ScreeningInfo(
                MovieInfo.from(movie), ScreenInfo.from(screen), screening.getStartTime(), screening.getEndTime());
    }
}
