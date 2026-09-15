package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.domain.Screen;
import com.ceos24.cgv.domain.theater.domain.ScreenType;
import com.ceos24.cgv.domain.theater.domain.Theater;
import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreenAdminService {

    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;

    @Transactional
    public void createScreen(Long theaterId, ScreenCreateRequest request) {
        Theater theater = theaterRepository.findById(theaterId).orElseThrow();
        Screen screen = new Screen(theater,request.screenType(), request.name());
        screenRepository.save(screen);
    }
}
