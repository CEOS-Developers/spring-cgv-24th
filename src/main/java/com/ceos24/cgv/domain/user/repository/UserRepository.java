package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Boolean existsByUsername(String name);

    Optional<User> findByUsernameAndIsLockAndIsSocial(String username, Boolean isLock, Boolean isSocial);
}
