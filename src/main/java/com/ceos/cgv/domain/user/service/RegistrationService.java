package com.ceos.cgv.domain.user.service;

import com.ceos.cgv.domain.user.dto.SignupRequest;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
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
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
    }
}
