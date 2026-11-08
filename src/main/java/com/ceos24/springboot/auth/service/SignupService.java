package com.ceos24.springboot.auth.service;

import com.ceos24.springboot.auth.dto.SignupRequest;
import com.ceos24.springboot.auth.dto.SignupResponse;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.user.domain.Role;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    public SignupResponse signupUser(SignupRequest request) {
        return signup(request, Role.USER);
    }

    public SignupResponse signupAdmin(SignupRequest request) {
        return signup(request, Role.ADMIN);
    }

    private SignupResponse signup(SignupRequest request, Role role) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.email())) {
            throw new GlobalException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호를 해시 처리하여 원문 대신 저장
        String encodedPassword =
                passwordEncoder.encode(request.password());

        User user = switch (role) {
            case USER -> User.create(
                    request.name(),
                    request.email(),
                    encodedPassword,
                    request.phone()
            );
            case ADMIN -> User.createAdmin(
                    request.name(),
                    request.email(),
                    encodedPassword,
                    request.phone()
            );
        };

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return SignupResponse.from(savedUser);

        } catch (DataIntegrityViolationException e) {
            if (isEmailUniqueViolation(e)) {
                throw new GlobalException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw e;
        }
    }

    // 이메일 중복여부 확인
    private boolean isEmailUniqueViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && matchesEmailConstraint(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    private boolean matchesEmailConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }

        String name = constraintName
                .replace("\"", "")
                .replace("`", "")
                .trim();

        // H2: PUBLIC.UK_USERS_EMAIL INDEX PUBLIC.UK_USERS_EMAIL_INDEX_...
        int indexPosition = name.toUpperCase(Locale.ROOT).indexOf(" INDEX ");
        if (indexPosition >= 0) {
            name = name.substring(0, indexPosition);
        }

        // 스키마명 또는 테이블명 접두사 제거
        int dotPosition = name.lastIndexOf('.');
        if (dotPosition >= 0) {
            name = name.substring(dotPosition + 1);
        }

        return EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(name.trim());
    }
}
