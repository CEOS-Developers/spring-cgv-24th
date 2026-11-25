package com.ceos.cgv.domain.movie.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningIndexIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void 영화별_상영_일정_복합_인덱스는_영화와_시작_시각_순서로_생성된다() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet indexes = connection.getMetaData().getIndexInfo(
                     connection.getCatalog(), null, "SCREENINGS", false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && indexName.equalsIgnoreCase("idx_screening_movie_start_at")) {
                    int position = indexes.getInt("ORDINAL_POSITION");
                    while (columns.size() < position) {
                        columns.add(null);
                    }
                    columns.set(position - 1, indexes.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }

        assertThat(columns).containsExactly("movie_id", "start_at");
    }
}
