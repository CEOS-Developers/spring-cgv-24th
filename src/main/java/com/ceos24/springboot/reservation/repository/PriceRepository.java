package com.ceos24.springboot.reservation.repository;

import com.ceos24.springboot.reservation.domain.AudienceType;
import com.ceos24.springboot.reservation.domain.DayType;
import com.ceos24.springboot.reservation.domain.Price;
import com.ceos24.springboot.reservation.domain.TimeType;
import com.ceos24.springboot.theater.domain.ScreenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findByAudienceTypeAndDayTypeAndTimeTypeAndScreenCategory(
            AudienceType audienceType,
            DayType dayType,
            TimeType timeType,
            ScreenType screenCategory
    );
}