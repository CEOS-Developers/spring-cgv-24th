package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.domain.Movie;
import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.movie.dto.ScheduleTimeInfo;
import com.ceos24.cgv.domain.movie.dto.ScreenScheduleInfo;
import com.ceos24.cgv.domain.movie.dto.ScreeningMovieInfo;
import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.dto.response.GetScreeningResponse;
import com.ceos24.cgv.domain.screening.domain.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.domain.Screen;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class MovieService {

    private final ScreeningRepository screeningRepository;

    @Transactional(readOnly = true)
    public GetMovieResponse getMovies(Long theaterId) {
        return new GetMovieResponse(
                screeningRepository.findDistinctMoviesByTheaterId(theaterId).stream()
                .map(MovieInfo::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public GetScreeningResponse getScreenings(Long theaterId) {

        List<Screening> screenings = screeningRepository.findAllByTheaterIdWithDetails(theaterId);

        Map<Movie, List<Screening>> groupedByMovie = screenings.stream()
                .collect(Collectors.groupingBy(Screening::getMovie));

        List<ScreeningMovieInfo> movieInfos = groupedByMovie.entrySet().stream()
                .map(movieEntry -> {
                    Movie movie = movieEntry.getKey();
                    List<Screening> movieScreenings = movieEntry.getValue();

                    Map<Screen, List<Screening>> groupedByScreen = movieScreenings.stream()
                            .collect(Collectors.groupingBy(Screening::getScreen));

                    List<ScreenScheduleInfo> screenInfos = groupedByScreen.entrySet().stream()
                            .map(screenEntry -> {
                                Screen screen = screenEntry.getKey();
                                List<Screening> screenScreenings = screenEntry.getValue();

                                List<ScheduleTimeInfo> scheduleInfos = screenScreenings.
                                        stream()
                                        .map(s -> new ScheduleTimeInfo(
                                                s.getId(),
                                                s.getStartTime(),
                                                s.getEndTime(),
                                                45L)).toList();
                                return new ScreenScheduleInfo(screen.getId(), screen.getName(),
                                        scheduleInfos);
                            })
                            .toList();

                    return new ScreeningMovieInfo(movie.getId(), movie.getTitle(), screenInfos);
                })
                .toList();

        return new GetScreeningResponse(theaterId, movieInfos);
    }
}
