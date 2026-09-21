package com.spring_cgv_24th.domain.store.repository;

import com.spring_cgv_24th.domain.store.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
