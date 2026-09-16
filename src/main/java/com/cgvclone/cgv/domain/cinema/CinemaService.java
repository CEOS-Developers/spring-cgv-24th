package com.cgvclone.cgv.domain.cinema;

import com.cgvclone.cgv.domain.cinema.dto.CinemaDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    @Transactional(readOnly = true)
    public CinemaDetailResponse getCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new IllegalArgumentException("Cinema not found"));
        return CinemaDetailResponse.from(cinema);
    }
}
