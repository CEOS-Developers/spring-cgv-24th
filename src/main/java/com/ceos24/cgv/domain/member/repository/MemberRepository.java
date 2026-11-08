package com.ceos24.cgv.domain.member.repository;

import com.ceos24.cgv.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String longinId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
