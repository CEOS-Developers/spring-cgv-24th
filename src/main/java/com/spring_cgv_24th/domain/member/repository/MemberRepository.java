package com.spring_cgv_24th.domain.member.repository;

import com.spring_cgv_24th.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
