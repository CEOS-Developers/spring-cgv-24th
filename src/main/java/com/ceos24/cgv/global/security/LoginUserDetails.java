package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// 로그인 한 번 동안만 쓰인다. 비밀번호 비교에 해시가 필요해서 들고 있고,
// 인증이 끝나면 ProviderManager가 eraseCredentials()를 불러 해시를 지운다.
// 토큰 검증 이후의 요청은 비밀번호가 없는 AuthUser를 쓴다.
@Getter
public class LoginUserDetails implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String loginId;
    private final Role role;
    private String password;

    public LoginUserDetails(User user) {
        this.userId = user.getId();
        this.loginId = user.getLoginId();
        this.role = user.getRole();
        this.password = user.getPassword();
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
