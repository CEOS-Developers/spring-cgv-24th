package com.ceos24.spring_cgv.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistRepository {

    private final StringRedisTemplate redisTemplate;

    /***
     * 함수 기능: AT의 jti를 블랙리스트에 등록한다.
     * @param jti 차단할 AT의 jti 클레임
     * @param ttl AT의 잔여 수명
     * @param reason 차단 사유
     */
    public void blacklist(String jti, Duration ttl, String reason){

        // 이미 만료된 AT는 저장 x
        if (ttl.isZero() || ttl.isNegative()){
            return;
        }
        redisTemplate.opsForValue().set(
                "bl:" + jti, reason, ttl
        );
    }

    /***
     * 함수 기능: 해당 jti가 블랙리스트에 등록되어있는지 확인한다.
     * @param jti 확인할 AT의 jti 클레임
     * @return 등록되어 있으면 true
     */
    public boolean isBlacklisted(String jti){

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("bl:" + jti));
        } catch (DataAccessException e){
            log.error("[블랙리스트 조회 실패] Redis 장애로 검사를 생략합니다.", e);
            return false;
        }
    }
}
