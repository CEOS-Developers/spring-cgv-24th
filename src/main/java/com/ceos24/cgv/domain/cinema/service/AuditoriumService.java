package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.dto.request.AuditoriumCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.AuditoriumResponse;
import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumRepository;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumTypeRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final AuditoriumTypeRepository auditoriumTypeRepository;
    private final CinemaRepository cinemaRepository;

    // 상영관 등록 로직
    @Transactional
    public Long createAuditorium(
            Long cinemaId, // url로 넘어옴
            AuditoriumCreateRequest request
    ) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        AuditoriumType auditoriumType = auditoriumTypeRepository.findById(request.auditoriumTypeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUDITORIUM_TYPE_NOT_FOUND));

        if (auditoriumRepository.existsByCinemaIdAndName(cinemaId, request.name())) {
            throw new BusinessException(ErrorCode.AUDITORIUM_NAME_DUPLICATED);
        }

        Auditorium auditorium = Auditorium.create(cinema, auditoriumType, request.name());
        auditoriumRepository.save(auditorium);
        return auditorium.getId();
    }

    // 상영관 목록 조회
    public List<AuditoriumResponse> getAuditoriums(Long cinemaId) {

        if (!cinemaRepository.existsById(cinemaId)) {
            throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
        }

        return auditoriumRepository.findAllByCinemaIdOrderByIdAsc(cinemaId)
                .stream()
                .map(AuditoriumResponse::from)
                .toList();
    }

    // todo: 단일 상영관 조회와 삭제 로직은 필요시 구현
}
