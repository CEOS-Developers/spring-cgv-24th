package com.ceos.cgv.domain.user.security;

import com.ceos.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CgvUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userRepository.findByLoginId(loginId.toLowerCase(Locale.ROOT))
                .filter(user -> user.getPasswordHash() != null)
                .map(CgvUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
