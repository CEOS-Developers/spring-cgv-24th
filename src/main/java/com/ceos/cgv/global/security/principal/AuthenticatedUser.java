package com.ceos.cgv.global.security.principal;

import com.ceos.cgv.domain.user.enums.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {
}
