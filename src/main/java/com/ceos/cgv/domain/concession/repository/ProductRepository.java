package com.ceos.cgv.domain.concession.repository;

import com.ceos.cgv.domain.concession.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
