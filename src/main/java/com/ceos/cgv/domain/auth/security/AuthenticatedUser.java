package com.ceos.cgv.domain.auth.security;

import com.ceos.cgv.domain.user.enums.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {
}
