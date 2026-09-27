package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 같은 토큰의 재발급을 한 줄로 세운다. 잠금 읽기는 스냅샷이 아니라 최신 커밋본을 읽어서,
    // 뒤 요청은 앞 요청이 남긴 사용 완료 표시를 본다.
    // user를 조인하지 않는다. MySQL의 FOR UPDATE는 조인된 users 행까지 잠가 같은 사용자의 다른 기기 재발급까지 줄을 서게 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
