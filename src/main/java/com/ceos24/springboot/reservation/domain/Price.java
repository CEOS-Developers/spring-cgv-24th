package com.ceos24.springboot.reservation.domain;

import com.ceos24.springboot.theater.domain.ScreenType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type")
    private AudienceType audienceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type")
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_type")
    private TimeType timeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_category")
    private ScreenType screenCategory;

    @Column(name = "ticket_price")
    private Integer ticketPrice;
}