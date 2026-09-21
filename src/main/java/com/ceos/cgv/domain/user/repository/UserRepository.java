package com.ceos.cgv.domain.user.repository;

import com.ceos.cgv.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
