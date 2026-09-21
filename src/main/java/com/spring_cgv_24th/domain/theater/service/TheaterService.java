package com.spring_cgv_24th.domain.theater.service;

import com.spring_cgv_24th.domain.theater.dto.TheaterResDTO;
import com.spring_cgv_24th.domain.theater.dto.TheaterReqDTO;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.domain.store.entity.TheaterStock;
import com.spring_cgv_24th.domain.store.repository.ProductRepository;
import com.spring_cgv_24th.domain.store.repository.TheaterStockRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterService {

    private final TheaterRepository theaterRepository;
    private final ProductRepository productRepository;
    private final TheaterStockRepository theaterStockRepository;

    @Transactional
    public TheaterResDTO createTheater(TheaterReqDTO.CreateTheaterReqDTO request) {
        Theater theater = Theater.builder()
                .name(request.name())
                .address(request.address())
                .build();
        Theater savedTheater = theaterRepository.save(theater);
        theaterStockRepository.saveAll(productRepository.findAll(Sort.by("id")).stream()
                .map(product -> TheaterStock.builder()
                        .theater(savedTheater)
                        .product(product)
                        .quantity(1)
                        .build())
                .toList());
        return TheaterResDTO.from(savedTheater);
    }

    public TheaterResDTO getTheater(Long theaterId) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));
        return TheaterResDTO.from(theater);
    }

    public List<TheaterResDTO> getTheaters() {
        return theaterRepository.findAll(Sort.by("id")).stream()
                .map(TheaterResDTO::from)
                .toList();
    }
}
