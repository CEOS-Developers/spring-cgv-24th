package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.concession.dto.ProductCreateRequest;
import com.ceos.cgv.domain.concession.entity.Product;
import com.ceos.cgv.domain.concession.repository.ProductRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Product create(ProductCreateRequest request) {
        return productRepository.save(new Product(request.name(), request.price(), request.description()));
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
