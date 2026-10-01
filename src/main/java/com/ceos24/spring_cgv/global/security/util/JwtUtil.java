package com.ceos24.spring_cgv.global.security.util;

import com.ceos24.spring_cgv.domain.auth.enums.TokenType;
import com.ceos24.spring_cgv.domain.auth.exception.AuthException;
import com.ceos24.spring_cgv.domain.auth.exception.code.AuthErrorCode;
import com.ceos24.spring_cgv.domain.member.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;
    private final long atValidity;
    private final long rtValidity;

    public JwtUtil(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.at-validity}") long atValidity,
            @Value("${jwt.rt-validity}") long rtValidity
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.atValidity = atValidity;
        this.rtValidity = rtValidity;
    }

    // ms(밀리초) -> s(초) 단위로 변경
    public long getAtValiditySeconds() {return atValidity/1000;}
    public long getRtValiditySeconds() {return rtValidity/1000;}

    public String createAT(Long memberId, Role role) {

        Date now = new Date();
        Date exp = new Date(now.getTime() + atValidity);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim("typ", TokenType.AT.name())
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey)
                .compact();
    }

    public String createRT(Long memberId){

        Date now = new Date();
        Date exp = new Date(now.getTime() + rtValidity);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim("typ", TokenType.RT.name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAT(String token) {
        return parseClaims(token, TokenType.AT);
    }

    public Claims parseRT(String token){
        return parseClaims(token, TokenType.RT);
    }

    /***
     * 함수 기능: 토큰의 잔여 수명을 계산한다.
     * @param claims 토큰의 클레임
     * @return 잔여시간
     */
    public long getRemainingMillis(Claims claims){
        long remainTime = claims.getExpiration().getTime() - System.currentTimeMillis();

        return Math.max(remainTime, 0);
    }

    private Claims parseClaims(String token, TokenType expected) {
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String typ = claims.get("typ", String.class);
            if (!expected.name().equals(typ)){
                log.warn("토큰 타입이 일치하지 않습니다. 기대={}, 실제={}", expected, typ);
                throw new AuthException(AuthErrorCode.TOKEN_TYPE_MISMATCH);
            }

            return claims;
        } catch (ExpiredJwtException e){
            log.warn("만료된 토큰입니다. {}", e.getMessage());
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e){
            log.warn("유효하지 않는 토큰입니다. {}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
