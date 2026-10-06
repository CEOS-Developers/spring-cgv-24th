package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DaoAuthenticationProvider가 로그인 중에 호출한다. 우리 코드가 직접 부르지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // 계정 없음을 그대로 알려도 되는 이유: DaoAuthenticationProvider가 이 예외를
    // BadCredentialsException으로 바꿔 비밀번호 틀림과 구분되지 않게 만든다.
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userRepository.findByLoginId(loginId)
                .map(LoginUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(loginId));
    }
}
