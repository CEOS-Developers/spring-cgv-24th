package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    //로그인 id로  조회
    Optional<User> findByLoginId(String loginId);

    //중복 아이디 조회
    boolean existsByLoginId(String loginId);

}