package com.spring_cgv_24th.domain.auditorium.service;

import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumReqDTO;
import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumResDTO;
import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumRepository;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumTypeRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final AuditoriumTypeRepository auditoriumTypeRepository;
    private final TheaterRepository theaterRepository;

    @Transactional
    public AuditoriumResDTO createAuditorium(Long theaterId, AuditoriumReqDTO.CreateAuditoriumDTO request) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));
        AuditoriumType type = auditoriumTypeRepository.findByKind(request.kind())
                .orElseThrow(() -> new CustomException(ErrorCode.AUDITORIUM_TYPE_NOT_FOUND));

        Auditorium auditorium = Auditorium.builder()
                .theater(theater)
                .type(type)
                .name(request.name().strip())
                .build();

        return AuditoriumResDTO.from(auditoriumRepository.save(auditorium));
    }

    @Transactional(readOnly = true)
    public List<AuditoriumResDTO> getAuditoriums(Long theaterId) {
        if (!theaterRepository.existsById(theaterId)) {
            throw new CustomException(ErrorCode.THEATER_NOT_FOUND);
        }

        return auditoriumRepository.findAllByTheaterIdOrderByIdAsc(theaterId).stream()
                .map(AuditoriumResDTO::from)
                .toList();
    }
}
