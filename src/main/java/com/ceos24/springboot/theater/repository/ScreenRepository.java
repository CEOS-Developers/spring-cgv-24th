package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

// 클라이언트가 조회하지는 않겠지만 상영회차를 조회할때 필요하므로 작성함.
public interface ScreenRepository extends JpaRepository<Screen, Long> {

}