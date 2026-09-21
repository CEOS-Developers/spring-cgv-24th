package com.spring_cgv_24th.domain.screening.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumRepository;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.domain.screening.dto.request.ScreeningReqDTO;
import com.spring_cgv_24th.domain.screening.dto.response.ScreeningResDTO;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import com.spring_cgv_24th.domain.screening.repository.ScreeningRepository;
import com.spring_cgv_24th.domain.screening.repository.ScreeningSeatRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock private MovieRepository movieRepository;
    @Mock private AuditoriumRepository auditoriumRepository;
    @Mock private ScreeningRepository screeningRepository;
    @Mock private ScreeningSeatRepository screeningSeatRepository;
    @Captor private ArgumentCaptor<Iterable<ScreeningSeat>> seatsCaptor;
    @InjectMocks private ScreeningService screeningService;

    @Test
    void invalidMovieDurationReturnsSpecificConfigurationError() {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(auditorium.getType()).thenReturn(mock(AuditoriumType.class));
        when(movie.getDurationMinutes()).thenReturn((short) 0);

        CustomException error = assertThrows(CustomException.class,
                () -> screeningService.createScreening(new ScreeningReqDTO.CreateScreeningDTO(
                        1L, 2L, LocalDateTime.of(2026, 9, 16, 14, 0))));

        assertEquals(ErrorCode.INVALID_MOVIE_DURATION, error.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getErrorCode().getHttpStatus());
        assertEquals("MOVIE_DURATION500", error.getErrorCode().getCode());
        verifyNoInteractions(screeningRepository, screeningSeatRepository);
    }

    @Test
    void invalidAuditoriumRowCountReturnsSpecificConfigurationError() {
        assertInvalidAuditoriumDimensions((short) 0, (short) 1);
    }

    @Test
    void invalidAuditoriumColumnCountReturnsSpecificConfigurationError() {
        assertInvalidAuditoriumDimensions((short) 1, (short) 0);
    }

    @Test
    void overlappingScreeningIsRejectedBeforeSaving() {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        AuditoriumType type = AuditoriumType.builder()
                .kind(AuditoriumKind.GENERAL)
                .rowCount((short) 2)
                .columnCount((short) 3)
                .basePrice(14_000)
                .build();
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 16, 14, 0);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(movie.getDurationMinutes()).thenReturn((short) 120);
        when(auditorium.getType()).thenReturn(type);
        when(auditorium.getId()).thenReturn(2L);
        when(screeningRepository.existsOverlapping(2L, startsAt, startsAt.plusMinutes(120)))
                .thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> screeningService.createScreening(
                        new ScreeningReqDTO.CreateScreeningDTO(1L, 2L, startsAt)));

        assertEquals(ErrorCode.SCREENING_OVERLAP, error.getErrorCode());
        verify(screeningRepository).existsOverlapping(2L, startsAt, startsAt.plusMinutes(120));
        verify(screeningRepository, never()).save(any(Screening.class));
        verifyNoInteractions(screeningSeatRepository);
    }

    @Test
    void newScreeningCreatesEverySeatWithGeneralPrice() {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        Theater theater = mock(Theater.class);
        AuditoriumType type = AuditoriumType.builder()
                .kind(AuditoriumKind.GENERAL)
                .rowCount((short) 2)
                .columnCount((short) 3)
                .basePrice(14_000)
                .build();
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 16, 14, 0);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(movie.getDurationMinutes()).thenReturn((short) 120);
        when(auditorium.getType()).thenReturn(type);
        when(auditorium.getId()).thenReturn(2L);
        when(auditorium.getTheater()).thenReturn(theater);
        when(screeningRepository.save(any(Screening.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScreeningResDTO response = screeningService.createScreening(
                new ScreeningReqDTO.CreateScreeningDTO(1L, 2L, startsAt));

        verify(screeningSeatRepository).saveAll(seatsCaptor.capture());
        List<ScreeningSeat> seats = new ArrayList<>();
        seatsCaptor.getValue().forEach(seats::add);
        assertEquals(6, response.totalSeats());
        assertEquals(startsAt.plusMinutes(120), response.endsAt());
        assertEquals(6, seats.size());
        assertEquals(List.of("1-1", "1-2", "1-3", "2-1", "2-2", "2-3"),
                seats.stream().map(seat -> seat.getRowNo() + "-" + seat.getColumnNo()).toList());
        seats.forEach(seat -> assertEquals(14_000, seat.getPrice()));
    }

    @Test
    void priceChangeAffectsOnlyNewScreenings() {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        AuditoriumType type = AuditoriumType.builder()
                .kind(AuditoriumKind.GENERAL)
                .rowCount((short) 1)
                .columnCount((short) 1)
                .basePrice(14_000)
                .build();
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(movie.getDurationMinutes()).thenReturn((short) 120);
        when(auditorium.getType()).thenReturn(type);
        when(auditorium.getId()).thenReturn(2L);
        when(auditorium.getTheater()).thenReturn(mock(Theater.class));
        when(screeningRepository.save(any(Screening.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        screeningService.createScreening(new ScreeningReqDTO.CreateScreeningDTO(
                1L, 2L, LocalDateTime.of(2026, 9, 16, 14, 0)));
        type.updateBasePrice(16_000);
        screeningService.createScreening(new ScreeningReqDTO.CreateScreeningDTO(
                1L, 2L, LocalDateTime.of(2026, 9, 17, 14, 0)));

        verify(screeningSeatRepository, times(2)).saveAll(seatsCaptor.capture());
        List<ScreeningSeat> firstSeats = new ArrayList<>();
        List<ScreeningSeat> secondSeats = new ArrayList<>();
        seatsCaptor.getAllValues().get(0).forEach(firstSeats::add);
        seatsCaptor.getAllValues().get(1).forEach(secondSeats::add);
        assertEquals(14_000, firstSeats.getFirst().getPrice());
        assertEquals(16_000, secondSeats.getFirst().getPrice());
    }

    @Test
    void invalidBasePricePreventsScreeningCreation() {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        AuditoriumType type = mock(AuditoriumType.class);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(movie.getDurationMinutes()).thenReturn((short) 120);
        when(auditorium.getType()).thenReturn(type);
        when(auditorium.getId()).thenReturn(2L);
        when(type.getRowCount()).thenReturn((short) 1);
        when(type.getColumnCount()).thenReturn((short) 1);
        when(type.getBasePrice()).thenReturn(0);

        CustomException error = assertThrows(CustomException.class,
                () -> screeningService.createScreening(new ScreeningReqDTO.CreateScreeningDTO(
                        1L, 2L, LocalDateTime.of(2026, 9, 16, 14, 0))));

        assertEquals(ErrorCode.TICKET_PRICE_CONFIG_INVALID, error.getErrorCode());
        verify(screeningRepository, never()).save(any(Screening.class));
        verifyNoInteractions(screeningSeatRepository);
    }

    private void assertInvalidAuditoriumDimensions(short rowCount, short columnCount) {
        Movie movie = mock(Movie.class);
        Auditorium auditorium = mock(Auditorium.class);
        AuditoriumType type = AuditoriumType.builder()
                .kind(AuditoriumKind.GENERAL)
                .rowCount(rowCount)
                .columnCount(columnCount)
                .basePrice(14_000)
                .build();
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(auditoriumRepository.findById(2L)).thenReturn(Optional.of(auditorium));
        when(auditorium.getType()).thenReturn(type);
        when(movie.getDurationMinutes()).thenReturn((short) 120);

        CustomException error = assertThrows(CustomException.class,
                () -> screeningService.createScreening(new ScreeningReqDTO.CreateScreeningDTO(
                        1L, 2L, LocalDateTime.of(2026, 9, 16, 14, 0))));

        assertEquals(ErrorCode.INVALID_AUDITORIUM_CONFIG, error.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getErrorCode().getHttpStatus());
        assertEquals("AUDITORIUM_CONFIG500", error.getErrorCode().getCode());
        verifyNoInteractions(screeningRepository, screeningSeatRepository);
    }
}
