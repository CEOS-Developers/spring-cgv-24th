package com.ceos24.springboot.shop.repository;

import com.ceos24.springboot.shop.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {
}