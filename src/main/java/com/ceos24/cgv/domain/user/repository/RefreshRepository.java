package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshRepository extends JpaRepository<RefreshEntity, Long> {

    Boolean existsByRefresh(String refreshToken);

    void deleteByRefresh(String refresh);

    void deleteByUsername(String username);
}
