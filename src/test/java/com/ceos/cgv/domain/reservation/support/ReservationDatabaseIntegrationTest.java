package com.ceos.cgv.domain.reservation.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfilesResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the same reservation scenarios on H2 or an isolated MySQL container. */
@ActiveProfiles(resolver = ReservationDatabaseIntegrationTest.DatabaseProfilesResolver.class)
public abstract class ReservationDatabaseIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private Environment environment;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (usesMysql()) {
            MySQLContainer mysql = MysqlDatabase.CONTAINER;
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
        }
    }

    @BeforeEach
    void verifyDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (usesMysql()) {
                assertThat(environment.getActiveProfiles()).containsExactly("mysql-test");
                assertThat(metadata.getDatabaseProductName()).isEqualTo("MySQL");
                assertThat(metadata.getDatabaseProductVersion()).startsWith("8.4.8");
                assertThat(connection.getCatalog()).isEqualTo("cgv_mysql_test");
                assertThat(metadata.getUserName()).startsWith("cgv_test@");
            } else {
                assertThat(environment.getActiveProfiles()).containsExactly("test");
                assertThat(metadata.getDatabaseProductName()).isEqualTo("H2");
            }
            System.out.printf("Database verification: engine=%s, version=%s, profile=%s%n",
                    metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
                    String.join(",", environment.getActiveProfiles()));
        }
    }

    private static boolean usesMysql() {
        return "mysql".equals(System.getProperty("cgv.test.database", "h2"));
    }

    public static final class DatabaseProfilesResolver implements ActiveProfilesResolver {
        @Override
        public String[] resolve(Class<?> testClass) {
            return new String[]{usesMysql() ? "mysql-test" : "test"};
        }
    }

    private static final class MysqlDatabase {
        // No host volume or reusable container: create/drop stays in this disposable DB.
        private static final MySQLContainer CONTAINER = startContainer();

        private static MySQLContainer startContainer() {
            MySQLContainer mysql = new MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("cgv_mysql_test")
                    .withUsername("cgv_test")
                    .withPassword(UUID.randomUUID().toString());
            mysql.start();
            return mysql;
        }
    }
}
