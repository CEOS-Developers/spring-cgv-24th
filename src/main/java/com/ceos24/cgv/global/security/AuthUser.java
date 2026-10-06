package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// 검증을 통과한 토큰에서만 만들어진다. 요청마다 DB를 보지 않으므로 비밀번호를 가질 이유가 없다.
public record AuthUser(
        Long userId,
        Role role
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    // 토큰 인증 이후에는 비밀번호를 비교할 일이 없다.
    @Override
    public String getPassword() {
        return null;
    }

    // 토큰의 sub가 userId라 loginId 없이도 사용자를 식별할 수 있다.
    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
