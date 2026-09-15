package com.ceos24.spring_cgv.domain.member.repository;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
