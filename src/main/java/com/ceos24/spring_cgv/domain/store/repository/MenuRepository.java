package com.ceos24.spring_cgv.domain.store.repository;

import com.ceos24.spring_cgv.domain.store.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {
}
