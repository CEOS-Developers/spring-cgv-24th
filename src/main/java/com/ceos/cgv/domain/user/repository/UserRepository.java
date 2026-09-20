package com.ceos.cgv.domain.user.repository;

import com.ceos.cgv.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByLoginId(String loginId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByLoginId(String loginId);
}
