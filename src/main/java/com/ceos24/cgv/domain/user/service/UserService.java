package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.request.UserRequest;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

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

        UserEntity userEntity = UserEntity.createLocalUser(
                request.username(),
                passwordEncoder.encode(request.password())
        );

        return userRepository.save(userEntity).getId();
    }


    //자체 로그인
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        UserEntity userEntity = userRepository.findByUsernameAndIsLockAndIsSocial(username, false, false)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return User.builder()
                .username(userEntity.getUsername())
                .password(userEntity.getPassword())
                .roles(userEntity.getRoleType().name())
                .accountLocked(userEntity.getIsLock())
                .build();
    }

    // 자체 로그인 회원 정보 수정
    public Long updateUser(UserRequest request) throws AccessDeniedException {
        String sessionUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!sessionUsername.equals(request.username())) {
            throw new AccessDeniedException("본인 계정만 수정 가능");
        }

        UserEntity userEntity = userRepository.findByUsernameAndIsLockAndIsSocial(
                request.username(),
                false,
                false
        ).orElseThrow(() -> new UsernameNotFoundException(request.username()));

        userEntity.updateUser(request);

        return userRepository.save(userEntity).getId();
    }


    // 자체/소셜 로그인 회원 탈퇴

    // 소셜 로그인

    // 자체/소셜 유저 정보 조회
}
