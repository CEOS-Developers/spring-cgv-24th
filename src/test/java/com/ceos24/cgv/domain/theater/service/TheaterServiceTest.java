package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.TheaterCreateRequest;
import com.ceos24.cgv.domain.theater.dto.request.TheaterUpdateRequest;
import com.ceos24.cgv.domain.theater.dto.response.TheaterResponse;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.theater;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {

    @Mock
    private TheaterRepository theaterRepository;

    @InjectMocks
    private TheaterService theaterService;

    @Test
    void 영화관을_생성하고_ID를_반환한다() {
        Theater saved = theater(1L);
        when(theaterRepository.save(any(Theater.class))).thenReturn(saved);

        Long result = theaterService.create(new TheaterCreateRequest("CGV 용산", "서울", "용산구", "설명", "image"));

        ArgumentCaptor<Theater> captor = ArgumentCaptor.forClass(Theater.class);
        verify(theaterRepository).save(captor.capture());
        assertThat(result).isEqualTo(1L);
        assertThat(captor.getValue().getName()).isEqualTo("CGV 용산");
        assertThat(captor.getValue().getRegion()).isEqualTo("서울");
    }

    @Test
    void 영화관_목록을_응답으로_변환한다() {
        when(theaterRepository.findAll()).thenReturn(List.of(theater(1L), theater(2L)));

        List<TheaterResponse> result = theaterService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void 존재하는_영화관을_수정한다() {
        Theater target = theater(1L);
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(target));

        theaterService.update(1L, new TheaterUpdateRequest("CGV 왕십리", null, null, "새 설명", null));

        assertThat(target.getName()).isEqualTo("CGV 왕십리");
        assertThat(target.getDescription()).isEqualTo("새 설명");
    }

    @Test
    void 존재하지_않는_영화관을_조회하면_예외가_발생한다() {
        when(theaterRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> theaterService.findById(999L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(TheaterErrorStatus.THEATER_NOT_FOUND);
    }

    @Test
    void 영화관을_삭제한다() {
        Theater target = theater(1L);
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(target));

        theaterService.delete(1L);

        verify(theaterRepository).delete(target);
    }
}
