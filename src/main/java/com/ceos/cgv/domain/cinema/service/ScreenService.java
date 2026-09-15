package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.domain.cinema.dto.ScreenCreateRequest;
import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.repository.ScreenRepository;
import com.ceos.cgv.domain.cinema.enums.ScreenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScreenService {
    private final CinemaService cinemaService;
    private final ScreenRepository screenRepository;

    public Screen create(ScreenCreateRequest request) {
        Cinema cinema = cinemaService.findById(request.cinemaId());
        Screen screen = new Screen(
                cinema,
                request.screenType(),
                request.rowCount(),
                request.seatsPerRow()
        );
        return screenRepository.save(screen);
    }
}
