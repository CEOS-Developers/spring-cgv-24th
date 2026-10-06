package com.spring_cgv_24th.global.security.principal;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.global.jwt.AccessTokenClaims;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final Long memberId;
    private final String username;
    private final String password;
    private final MemberRole role;

    private CustomUserDetails(Long memberId, String username, String password, MemberRole role) {
        this.memberId = memberId;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public static CustomUserDetails from(Member member) {
        return new CustomUserDetails(
                member.getId(),
                member.getEmail(),
                member.getPasswordHash(),
                member.getRole());
    }

    public static CustomUserDetails from(AccessTokenClaims claims) {
        return new CustomUserDetails(
                claims.memberId(),
                claims.memberId().toString(),
                null,
                claims.role());
    }

    public Long getMemberId() {
        return memberId;
    }

    public MemberRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
