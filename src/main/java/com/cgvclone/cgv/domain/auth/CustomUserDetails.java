package com.cgvclone.cgv.domain.auth;

import com.cgvclone.cgv.domain.user.User;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public class CustomUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long userId;

    public CustomUserDetails(User user) {
        super(user.getEmail(), user.getPassword(), List.of());
        this.userId = user.getUserId();
    }

    public CustomUserDetails(VerifiedAccessToken token) {
        super(token.userId().toString(), "", token.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList());
        this.userId = token.userId();
        eraseCredentials();
    }
}
