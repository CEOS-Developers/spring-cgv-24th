package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
