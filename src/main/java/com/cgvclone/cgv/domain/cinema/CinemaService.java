package com.cgvclone.cgv.domain.cinema;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.cinema.dto.CinemaDetailResponse;
import com.cgvclone.cgv.domain.cinema.dto.CinemaListResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    @Transactional(readOnly = true)
    public Cinema getCinemaEntity(Long cinemaId) {
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new GlobalException(ErrorCode.CINEMA_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CinemaListResponse getCinemas(Long regionId) {
        List<Cinema> cinemas = regionId == null
                ? cinemaRepository.findAll()
                : cinemaRepository.findByRegion_RegionId(regionId);
        return CinemaListResponse.from(cinemas);
    }

    @Transactional(readOnly = true)
    public CinemaDetailResponse getCinema(Long cinemaId) {
        Cinema cinema = getCinemaEntity(cinemaId);
        return CinemaDetailResponse.from(cinema);
    }
}
