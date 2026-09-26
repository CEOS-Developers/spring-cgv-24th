package com.ceos.cgv.global.security;

import com.ceos.cgv.domain.user.enums.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {
}
