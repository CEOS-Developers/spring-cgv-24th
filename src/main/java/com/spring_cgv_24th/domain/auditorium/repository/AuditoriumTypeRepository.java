package com.spring_cgv_24th.domain.auditorium.repository;

import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumTypeRepository extends JpaRepository<AuditoriumType, Long> {

    Optional<AuditoriumType> findByKind(AuditoriumKind kind);
}
