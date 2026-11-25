package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.SignupRequest;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 메서드 전체를 트랜잭션으로 묶지 않는다. bcrypt 해시는 수십~수백 ms의 CPU 작업인데,
    // 트랜잭션 안에서 실행하면 그동안 DB 커넥션을 점유해 작은 풀(배포 프로필 5개)이 쉽게 고갈된다.
    // 중복 확인은 읽기 하나씩, 저장은 saveAndFlush 하나의 쓰기 트랜잭션으로 처리하고,
    // 동시 가입의 최종 방어선은 유니크 제약과 아래의 중복 키 변환이다.
    public User register(SignupRequest request) {
        String loginId = request.loginId().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (request.password().length() < 8
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.register(loginId, request.name(), email,
                passwordEncoder.encode(request.password()));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateKey(exception)) {
                throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    private static boolean isDuplicateKey(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && ("23505".equals(sqlException.getSQLState())
                    || ("23000".equals(sqlException.getSQLState())
                    && sqlException.getErrorCode() == 1062))) {
                return true;
            }
        }
        return false;
    }
}
