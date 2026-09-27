package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.request.UserRequest;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.entity.UserRoleType;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 자체 로그인 회원 가입(존재 여부)
    private Boolean existUser(UserRequest request) {
        return userRepository.existsByUsername(request.username());
    }

    // 자체 로그인 회원 가입
    @Transactional
    public Long addUser(UserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.createLocalUser(
                request.username(),
                passwordEncoder.encode(request.password())
        );

        return userRepository.save(user).getId();
    }


    //자체 로그인

    // 자체 로그인 회원 정보 수정
    public Long updateUser(UserRequest request) throws AccessDeniedException {
        String sessionUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!sessionUsername.equals(request.username())) {
            throw new AccessDeniedException("본인 계정만 수정 가능");
        }

        User user = userRepository.findByUsernameAndIsLockAndIsSocial(
                request.username(),
                false,
                false
        ).orElseThrow(() -> new UsernameNotFoundException(request.username()));

        user.updateUser(request);

        return userRepository.save(user).getId();
    }


    // 자체/소셜 로그인 회원 탈퇴

    // 소셜 로그인

    // 자체/소셜 유저 정보 조회
}
