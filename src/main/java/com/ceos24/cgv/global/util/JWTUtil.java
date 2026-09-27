package com.ceos24.cgv.global.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JWTUtil {

    private static SecretKey secretKey;
    private static Long accessTokenExpiresIn;
    private static Long refreshTokenExpiresIn;

    @Value("${jwt.secret}")
    public void setSecretKey(String secretKeyString) {
        JWTUtil.secretKey = new SecretKeySpec(secretKeyString.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
    }

    @Value("${jwt.access-token-expiration}")
    public void setAccessTokenExpiresIn(Long expiration) {
        JWTUtil.accessTokenExpiresIn = expiration;
    }

    @Value("${jwt.refresh-token-expiration}")
    public void setRefreshTokenExpiresIn(Long expiration) {
        JWTUtil.refreshTokenExpiresIn = expiration;
    }

    public static String getUsername(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("sub", String.class);
    }

    public static String getRole(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("role", String.class);
    }

    // JWT 유효 여부 (위조, 시간, Access/Refresh 여부)
    public static Boolean isValid(String token, Boolean isAccess) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            if (type == null) return false;

            if (isAccess && !type.equals("access")) return false;
            if (!isAccess && !type.equals("refresh")) return false;

            return true;

        }catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // JWT(Access/Refresh) 생성
    public static String createJWT(String username, String role, boolean isAccess) {
        long now = System.currentTimeMillis();
        long expiry = isAccess ? accessTokenExpiresIn : refreshTokenExpiresIn;
        String type = isAccess ? "access" : "refresh";

        return Jwts.builder()
                .claim("sub", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiry))
                .signWith(secretKey)
                .compact();
    }
}
