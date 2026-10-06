package com.ceos24.cgv.domain.store.repository;

import com.ceos24.cgv.domain.store.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}
