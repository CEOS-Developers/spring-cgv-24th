package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.domain.concession.dto.InventoryCreateRequest;
import com.ceos.cgv.domain.concession.entity.Inventory;
import com.ceos.cgv.domain.concession.entity.Product;
import com.ceos.cgv.domain.concession.repository.InventoryRepository;
import com.ceos.cgv.domain.concession.repository.ProductRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final CinemaRepository cinemaRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public Inventory create(InventoryCreateRequest request) {
        Cinema cinema = cinemaRepository.findById(request.cinemaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (inventoryRepository.existsByCinema_IdAndProduct_Id(request.cinemaId(), request.productId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_INVENTORY);
        }
        return inventoryRepository.save(new Inventory(cinema, product, request.stockQuantity()));
    }
}
