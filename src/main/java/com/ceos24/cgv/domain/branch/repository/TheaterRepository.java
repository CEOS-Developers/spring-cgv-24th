package com.ceos24.cgv.domain.branch.repository;

import com.ceos24.cgv.domain.branch.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
    List<Theater> findByBranchId(Long branchId);
}
