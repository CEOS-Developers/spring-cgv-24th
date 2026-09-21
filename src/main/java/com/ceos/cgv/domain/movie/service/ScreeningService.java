package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.repository.ScreenRepository;
import com.ceos.cgv.domain.movie.dto.ScreeningCreateRequest;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreeningService {
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;

    @Transactional
    public Screening create(ScreeningCreateRequest request) {
        Movie movie = movieRepository.findByIdForShare(request.movieId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        movie.ensurePublic();
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREEN_NOT_FOUND));
        if (screen.getRowCount() < 1 || screen.getRowCount() > 26
                || screen.getSeatsPerRow() < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Screening screening = screeningRepository.save(Screening.builder()
                .movie(movie)
                .screen(screen)
                .startAt(request.startAt())
                .build());
        List<ScreeningSeat> seats = new ArrayList<>();
        for (int row = 0; row < screen.getRowCount(); row++) {
            String seatRow = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= screen.getSeatsPerRow(); number++) {
                seats.add(new ScreeningSeat(screening, seatRow, number));
            }
        }
        screeningSeatRepository.saveAll(seats);
        return screening;
    }

    @Transactional(readOnly = true)
    public List<Screening> findAllByMovieId(Long movieId) {
        if (!movieRepository.existsByIdAndVisibility(movieId, MovieVisibility.PUBLIC)) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
        }
        return screeningRepository.findAllByMovie_IdOrderByStartAt(movieId);
    }
}
