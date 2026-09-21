package com.ceos24.cgv.domain.member.repository;

import com.ceos24.cgv.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String longinId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
