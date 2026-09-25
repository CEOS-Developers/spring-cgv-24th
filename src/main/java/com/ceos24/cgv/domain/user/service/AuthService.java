package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.ceos24.cgv.domain.user.dto.SignupResponse;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    public SignupResponse signup(SignupRequest req) {
        if (userRepository.existsByLoginId(req.loginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.builder()
                .loginId(req.loginId())
                .password(passwordEncoder.encode(req.password()))
                .name(req.name())
                .birthDate(req.birthDate())
                .email(req.email())
                .phoneNumber(req.phoneNumber())
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // pre-check와 INSERT 사이에 같은 아이디가 먼저 가입한 경우다.
            // users의 unique 제약은 login_id 하나뿐이라 다른 원인으로 오역될 여지가 없다.
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        return SignupResponse.from(user);
    }
}
