package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.CreateTheaterRequest;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TheaterAdminService {

    private final TheaterRepository theaterRepository;

    @Transactional
    public void createTheater(CreateTheaterRequest request) {
        Theater theater = new Theater(request.name(), request.address());
        theaterRepository.save(theater);
    }
}
