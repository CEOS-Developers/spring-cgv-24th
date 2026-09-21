package com.ceos.cgv.domain.reservation.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase
class ReservedSeatSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 예매좌석은_예매를_통해서만_상영일정과_연결된다() {
        Integer screeningColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'RESERVED_SEATS'
                  AND UPPER(COLUMN_NAME) = 'SCREENING_ID'
                """, Integer.class);

        assertThat(screeningColumnCount).isZero();
    }
}
