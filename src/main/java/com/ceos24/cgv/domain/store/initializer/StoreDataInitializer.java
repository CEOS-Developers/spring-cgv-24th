package com.ceos24.cgv.domain.store.initializer;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoreDataInitializer implements ApplicationRunner {

    private static final int INITIAL_STOCK_QUANTITY = 100;

    private static final List<ProductSeed> PRODUCT_SEEDS = List.of(
            new ProductSeed("고소팝콘(M)", 5_000, "고소한 오리지널 팝콘"),
            new ProductSeed("카라멜팝콘(M)", 6_000, "달콤한 카라멜 팝콘"),
            new ProductSeed("콜라(M)", 3_000, "시원한 탄산음료"),
            new ProductSeed("나쵸", 4_500, "치즈 소스가 포함된 나쵸")
    );

    private final ProductRepository productRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaStockRepository cinemaStockRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Product> products = PRODUCT_SEEDS.stream()
                .map(this::saveProductIfAbsent)
                .toList();

        List<Cinema> cinemas = cinemaRepository.findAll();

        for (Cinema cinema : cinemas) {
            for (Product product : products) {
                saveCinemaStockIfAbsent(cinema, product);
            }
        }
    }

    private Product saveProductIfAbsent(ProductSeed seed) {
        return productRepository.findByName(seed.name())
                .orElseGet(() -> productRepository.save(
                        Product.create(
                                seed.name(),
                                seed.price(),
                                seed.description()
                        )
                ));
    }

    private void saveCinemaStockIfAbsent(Cinema cinema, Product product) {
        if (cinemaStockRepository.existsByCinemaIdAndProductId(
                cinema.getId(),
                product.getId()
        )) {
            return;
        }

        cinemaStockRepository.save(
                CinemaStock.create(
                        cinema,
                        product,
                        INITIAL_STOCK_QUANTITY
                )
        );
    }

    private record ProductSeed(
            String name,
            int price,
            String description
    ) {
    }
}
