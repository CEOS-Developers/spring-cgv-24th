package com.ceos24.cgv.domain.auth.repository;

import com.ceos24.cgv.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByToken(String token);

    void deleteByMemberId(Long memberId);

    void deleteByToken(String token);

    void deleteAllByMemberId(Long memberId);
}
