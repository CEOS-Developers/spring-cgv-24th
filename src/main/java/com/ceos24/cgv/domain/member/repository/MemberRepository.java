package com.ceos24.cgv.domain.member.repository;

import com.ceos24.cgv.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
