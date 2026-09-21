package com.ceos24.springboot.user.repository;

import com.ceos24.springboot.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}