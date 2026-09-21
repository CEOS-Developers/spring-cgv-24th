package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByName(String name);

    Optional<Product> findByName(String name);

    List<Product> findAllByOrderByIdAsc();
}
