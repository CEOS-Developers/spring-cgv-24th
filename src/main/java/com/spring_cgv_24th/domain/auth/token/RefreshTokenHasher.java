package com.spring_cgv_24th.domain.auth.token;

import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHasher {

    // 원문은 응답으로만 전달하고 저장 및 조회에는 동일한 SHA-256 해시를 사용한다.
    public String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    public boolean matches(String token, String storedHash) {
        String tokenHash = hash(token);
        return storedHash != null && MessageDigest.isEqual(
                tokenHash.getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
