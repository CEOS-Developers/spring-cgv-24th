package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
    List<Theater> findByBranchId(Long branchId);
}
