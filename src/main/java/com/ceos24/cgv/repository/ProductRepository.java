package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}
