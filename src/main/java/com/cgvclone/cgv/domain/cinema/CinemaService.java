package com.cgvclone.cgv.domain.cinema;

import com.cgvclone.cgv.domain.cinema.dto.CinemaDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    public CinemaDetailResponse getCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new IllegalArgumentException("Cinema not found"));
        return CinemaDetailResponse.from(cinema);
    }
}
