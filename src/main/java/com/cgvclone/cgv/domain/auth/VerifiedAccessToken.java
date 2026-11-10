package com.cgvclone.cgv.domain.auth;

import java.util.List;

public record VerifiedAccessToken(Long userId, List<String> authorities) {

    public VerifiedAccessToken {
        authorities = List.copyOf(authorities);
    }
}
