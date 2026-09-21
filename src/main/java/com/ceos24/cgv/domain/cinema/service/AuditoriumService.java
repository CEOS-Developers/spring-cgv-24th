package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.dto.request.AuditoriumCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.AuditoriumResponse;
import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.Seat;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumRepository;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumTypeRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final AuditoriumTypeRepository auditoriumTypeRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatRepository seatRepository;

    // 상영관 등록 로직
    @Transactional
    public Long createAuditorium(
            Long cinemaId, // url로 넘어옴
            AuditoriumCreateRequest request
    ) {
        // 시네마 존재여부 확인
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        // 상영관 종류 존재여부 확인
        AuditoriumType auditoriumType = auditoriumTypeRepository.findById(request.auditoriumTypeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUDITORIUM_TYPE_NOT_FOUND));

        // 상영관 이름 unique 확인
        if (auditoriumRepository.existsByCinemaIdAndName(cinemaId, request.name())) {
            throw new BusinessException(ErrorCode.AUDITORIUM_NAME_DUPLICATED);
        }

        // 상영관 저장
        Auditorium auditorium = Auditorium.create(cinema, auditoriumType, request.name());
        auditoriumRepository.save(auditorium);

        // 상영관 종류에 맞는 좌석 생성 후 저장
        List<Seat> seats = creatSeats(auditorium, auditoriumType.getRowCount(), auditoriumType.getColumnCount());
        seatRepository.saveAll(seats);

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

    // 상영관 좌석 생성 로직
    private List<Seat> creatSeats(
            Auditorium auditorium,
            int rowCount,
            int columnCount
    ) {
        List<Seat> seats = new ArrayList<>(rowCount * columnCount);

        for (int row = 1; row <= rowCount; row++) {
            for (int column = 1; column <= columnCount; column++) {
                seats.add(Seat.create(auditorium, row, column));
            }
        }

        return seats;
    }

    // todo: 단일 상영관 조회와 삭제 로직은 필요시 구현
}
