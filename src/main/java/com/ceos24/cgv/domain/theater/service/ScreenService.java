package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.ScreenInfo;
import com.ceos24.cgv.domain.theater.dto.response.GetScreenResponse;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreenService {

    private final ScreenRepository screenRepository;

    @Transactional(readOnly = true)
    public GetScreenResponse getScreens(Long theaterId) {

        List<ScreenInfo> list = screenRepository.findByTheaterId(theaterId).stream()
                .map(ScreenInfo::from)
                .toList();

        return new GetScreenResponse(theaterId, list);
    }
}
