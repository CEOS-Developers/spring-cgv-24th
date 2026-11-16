package com.ceos24.springboot.global.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {
    @Test
    void businessClockAlwaysUsesSeoul() {
        assertThat(new TimeConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
