package com.ceos24.cgv.domain.auth.service;

import com.ceos24.cgv.domain.auth.dto.request.SignupRequest;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signup(SignupRequest request) {

        if (userRepository.existsByLoginId(request.loginId())) {
            throw new GeneralException(UserErrorStatus.DUPLICATE_LOGIN_ID);
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        User user = User.create(
                request.loginId(),
                encodedPassword,
                request.nickname(),
                null
        );

        User savedUser = userRepository.save(user);

        return savedUser.getId();
    }
}
