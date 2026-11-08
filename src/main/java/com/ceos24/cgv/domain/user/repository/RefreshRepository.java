package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RefreshRepository extends JpaRepository<RefreshEntity, Long> {

    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    void deleteByTokenHash(String tokenHash);

    void deleteByUsername(String username);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
