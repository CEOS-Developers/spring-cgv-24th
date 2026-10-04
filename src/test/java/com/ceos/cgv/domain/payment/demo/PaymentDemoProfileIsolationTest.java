package com.ceos.cgv.domain.payment.demo;

import com.ceos.cgv.SpringCgv24thApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDemoProfileIsolationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void 잘못된_DB설정은_JPA의_create_drop_전에_차단하여_기존테이블을_보존한다() throws Exception {
        String url = "jdbc:h2:file:" + temporaryDirectory.resolve("existing-database");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY, name VARCHAR(50))");
            statement.execute("INSERT INTO users VALUES (987654, '기존 사용자')");
        }
        assertThatThrownBy(() -> new SpringApplicationBuilder(SpringCgv24thApplication.class)
                .web(WebApplicationType.NONE).profiles("payment-demo").run(
                        "--spring.datasource.url=" + url,
                        "--cgv.payment.toss.client-key=",
                        "--cgv.payment.toss.secret-key="))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("The payment-demo profile requires an isolated H2 in-memory database");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT name FROM users WHERE user_id=987654")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("기존 사용자");
        }
    }
}
