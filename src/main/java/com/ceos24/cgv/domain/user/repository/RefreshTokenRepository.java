package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 재발급은 토큰의 주인 id와 현재 role로 액세스 토큰을 만든다. 한 번에 가져와 쿼리를 하나로 줄인다.
    @Query("""
            SELECT rt FROM RefreshToken rt
            JOIN FETCH rt.user
            WHERE rt.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findWithUserByTokenHash(@Param("tokenHash") String tokenHash);
}
