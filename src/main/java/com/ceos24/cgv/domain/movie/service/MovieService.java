package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.movie.dto.ScheduleTimeInfo;
import com.ceos24.cgv.domain.movie.dto.ScreenScheduleInfo;
import com.ceos24.cgv.domain.movie.dto.ScreeningMovieInfo;
import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.dto.response.GetScreeningResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final ScreeningRepository screeningRepository;

    @Transactional(readOnly = true)
    public GetMovieResponse getAllMovies() {
        return new GetMovieResponse(
                movieRepository.findAll().stream().map(MovieInfo::from).toList());
    }

    @Transactional(readOnly = true)
    public GetMovieResponse getMovies(Long theaterId) {
        return new GetMovieResponse(screeningRepository.findDistinctMoviesByTheaterId(theaterId).stream()
                .map(MovieInfo::from)
                .toList());
    }

    /**
     * 특정 극장의 전체 상영 시간표를 조회합니다.
     * 조회된 상영 일정(Screening) 리스트를 '영화 -> 상영관 -> 시간표' 계층 구조로 변환하여 반환합니다.
     * (한 영화가 각 상영관 별로 어떤 시간대에 상영되는지)
     *
     * (응답 json 예시)
     * "data": {
     *     "theaterId": 1,
     *     "movies": [
     *       {
     *         "movieId": 1,
     *         "movieTitle": "인셉션",
     *         "screens": [
     *           {
     *             "screenId": 1,
     *             "screenName": "1관",
     *             "schedules": [
     *               {
     *                 "screeningId": 1,
     *                 "startTime": "2024-11-20T10:00:00",
     *                 "endTime": "2024-11-20T12:30:00",
     *                 "availableSeats": 120
     *               }
     *             ]
     *           }
     *         ]
     *       }
     *     ]
     *   }
     *
     * @param theaterId 조회할 영화관의 ID
     * @return 영화관 ID와 영화별 상영 시간표가 포함된 응답 객체
     */
    @Transactional(readOnly = true)
    public GetScreeningResponse getScreenings(Long theaterId) {
        // 1. 특정 영화관의 전체 상영 일정을 조회
        List<Screening> screenings = screeningRepository.findAllByTheaterIdWithDetails(theaterId);

        // 2. 상영 일정 리스트를 영화'를 기준으로 그룹핑
        Map<Movie, List<Screening>> groupedByMovie =
                screenings.stream().collect(Collectors.groupingBy(Screening::getMovie));

        // 3. 그룹핑된 데이터를 응답용 DTO 리스트로 변환
        List<ScreeningMovieInfo> movieInfos = createScreeningMovieInfos(groupedByMovie);

        return new GetScreeningResponse(theaterId, movieInfos);
    }

    /**
     * 영화별로 그룹핑된 상영 일정 데이터를 바탕으로 영화 정보(ScreeningMovieInfo) DTO 리스트를 생성합니다.
     */
    private List<ScreeningMovieInfo> createScreeningMovieInfos(Map<Movie, List<Screening>> groupedByMovie) {
        return groupedByMovie.entrySet().stream()
                .map(movieEntry -> {
                    Movie movie = movieEntry.getKey();
                    List<Screening> movieScreenings = movieEntry.getValue();

                    // 해당 영화의 상영 일정들을 다시 '상영관(Screen)'을 기준으로 2차 그룹핑
                    Map<Screen, List<Screening>> groupedByScreen =
                            movieScreenings.stream().collect(Collectors.groupingBy(Screening::getScreen));

                    List<ScreenScheduleInfo> screenInfos = createScreenScheduleInfos(groupedByScreen);

                    return new ScreeningMovieInfo(movie.getId(), movie.getTitle(), screenInfos);
                })
                .toList();
    }

    /**
     * 상영관별로 그룹핑된 상영 일정 데이터를 바탕으로 상영관 정보(ScreenScheduleInfo) DTO 리스트를 생성합니다.
     */
    private List<ScreenScheduleInfo> createScreenScheduleInfos(Map<Screen, List<Screening>> groupedByScreen) {
        return groupedByScreen.entrySet().stream()
                .map(screenEntry -> {
                    Screen screen = screenEntry.getKey();
                    List<Screening> screenScreenings = screenEntry.getValue();

                    List<ScheduleTimeInfo> scheduleInfos = createScheduleTimeInfos(screen, screenScreenings);
                    return new ScreenScheduleInfo(screen.getId(), screen.getName(), scheduleInfos);
                })
                .toList();
    }

    /**
     * 상영관의 특정 상영 일정 리스트를 바탕으로 상세 시간표(ScheduleTimeInfo) DTO 리스트를 생성합니다.
     */
    private List<ScheduleTimeInfo> createScheduleTimeInfos(Screen screen, List<Screening> screenScreenings) {
        return screenScreenings.stream()
                .map(s -> new ScheduleTimeInfo(s.getId(), s.getStartTime(), s.getEndTime(), screen.getTotalSeats()))
                .toList();
    }
}
