package com.ceos.cgv.global.security.principal;

import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record CgvUserDetails(Long userId, String username, String password, UserRole role)
        implements UserDetails {

    public static CgvUserDetails from(User user) {
        return new CgvUserDetails(user.getId(), user.getLoginId(),
                user.getPasswordHash(), user.getRole());
    }

    public static CgvUserDetails fromToken(Long userId, UserRole role) {
        return new CgvUserDetails(userId, userId.toString(), null, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "CgvUserDetails[userId=" + userId + ", username=" + username
                + ", role=" + role + ", password=[REDACTED]]";
    }
}
