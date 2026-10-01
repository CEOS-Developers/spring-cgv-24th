package com.ceos24.spring_cgv.domain.auth.repository;

import com.ceos24.spring_cgv.domain.auth.enums.RefreshTokenStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;

    /***
     * 함수 기능: 발급한 RT를 Redis에 저장한다.
     * @param memberId 회원 식별자
     * @param refreshToken 저장할 RT 원문
     * @param ttl 키 만료 시간
     */
    public void save(Long memberId, String refreshToken, Duration ttl){
        redisTemplate.opsForValue().set(
                "rt:" + memberId, hash(refreshToken), ttl
        );
    }

    /***
     * 함수 기능: 제시된 RT가 저장된 값과 일치하는지 대조한다.
     * @param memberId 회원 식별자
     * @param refreshToken 대조할 RT 원문
     * @return 일치 여부를 구분한 상태값 (NOT_FOUND, VALID, MISMATCH)
     */
    public RefreshTokenStatus verify(Long memberId, String refreshToken){

        String hashedRt = redisTemplate.opsForValue().get("rt:" + memberId);

        if (hashedRt == null){
            return RefreshTokenStatus.NOT_FOUND;
        }

        return hashedRt.equals(hash(refreshToken)) ? RefreshTokenStatus.VALID : RefreshTokenStatus.MISMATCH;
    }

    /***
     * 함수 기능: 해당 회원의 RT를 삭제한다. 재사용 감지와 로그아웃에서 사용.
     * @param memberId 회원 식별자
     */
    public void delete(Long memberId){
        redisTemplate.delete("rt:" + memberId);
    }

    /***
     * 함수 기능: RT 원문을 SHA-256으로 해싱한다.
     * @param token 해싱할 RT 원문
     * @return 64자 소문자 hex 문자열
     */
    private String hash(String token){
        try {
            // SHA-256 구현체를 얻는다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 토큰을 UTF-8 바이트로 바꿔 해싱한다. 결과는 항상 256비트이다.
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            // 256비트를 64자 소문자 hex로 변환한다.
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다." + e);
        }
    }
}
