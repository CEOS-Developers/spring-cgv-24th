package com.cgvclone.cgv.domain.cinema;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    List<Cinema> findByRegion_RegionId(Long regionId);
}
