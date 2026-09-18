package com.spring_cgv_24th.domain.auditorium.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumReqDTO;
import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumResDTO;
import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumRepository;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumTypeRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriumServiceTest {

    @Mock private AuditoriumRepository auditoriumRepository;
    @Mock private AuditoriumTypeRepository auditoriumTypeRepository;
    @Mock private TheaterRepository theaterRepository;
    @Captor private ArgumentCaptor<Auditorium> auditoriumCaptor;
    @InjectMocks private AuditoriumService auditoriumService;

    @Test
    void creationUsesSelectedKindAndTrimsName() {
        Theater theater = mock(Theater.class);
        AuditoriumType type = AuditoriumType.builder()
                .kind(AuditoriumKind.GENERAL)
                .rowCount((short) 8)
                .columnCount((short) 8)
                .basePrice(14_000)
                .build();
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater));
        when(auditoriumTypeRepository.findByKind(AuditoriumKind.GENERAL))
                .thenReturn(Optional.of(type));
        when(auditoriumRepository.save(any(Auditorium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditoriumResDTO response = auditoriumService.createAuditorium(1L,
                new AuditoriumReqDTO.CreateAuditoriumDTO("  1관  ", AuditoriumKind.GENERAL));

        verify(auditoriumRepository).save(auditoriumCaptor.capture());
        assertSame(theater, auditoriumCaptor.getValue().getTheater());
        assertSame(type, auditoriumCaptor.getValue().getType());
        assertEquals("1관", response.name());
        assertEquals(AuditoriumKind.GENERAL, response.kind());
        assertEquals(64, response.totalSeats());
    }

    @Test
    void missingKindDoesNotCreateAuditorium() {
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(auditoriumTypeRepository.findByKind(AuditoriumKind.IMAX))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> auditoriumService.createAuditorium(1L,
                        new AuditoriumReqDTO.CreateAuditoriumDTO("2관", AuditoriumKind.IMAX)));

        assertEquals(ErrorCode.AUDITORIUM_TYPE_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(auditoriumRepository);
    }
}
