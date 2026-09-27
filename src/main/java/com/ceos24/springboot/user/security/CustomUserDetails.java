package com.ceos24.springboot.user.security;

import com.ceos24.springboot.user.domain.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.ceos24.springboot.user.domain.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final Role role;

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole()
        );
    }

//  아직 user와 admin 구분하지 않음.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

// 로그인 아이디
    @Override
    public String getUsername() {
        return email;
    }

//  로그인 비밀번호
    @Override
    public String getPassword() {
        return password;
    }
}