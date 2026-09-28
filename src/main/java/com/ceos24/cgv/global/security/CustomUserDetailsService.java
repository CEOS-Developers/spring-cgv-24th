package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * 1. 전달받은 loginId로 회원을 조회.
 * 2. 회원이 없으면 UsernameNotFoundException 발생
 * 3. 회원이 있으면 CustomUserDetials에 담아 반환
 */
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId)
    {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "아이디 또는 비밀번호가 올바르지 않습니다."
                ));

        return new CustomUserDetails(user);
    }



}
