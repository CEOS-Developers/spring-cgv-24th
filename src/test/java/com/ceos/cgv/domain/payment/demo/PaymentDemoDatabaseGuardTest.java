package com.ceos.cgv.domain.payment.demo;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentDemoDatabaseGuardTest {
    @Test
    void 메모리_H2만_허용하고_파일_H2와_개발_DB는_기동_전에_차단한다() throws Exception {
        assertThatCode(() -> PaymentDemoDatabaseGuard.requireInMemoryH2(dataSource("jdbc:h2:mem:demo")))
                .doesNotThrowAnyException();
        for (String url : new String[]{"jdbc:mysql://localhost/cgv", "jdbc:h2:file:./cgv", "jdbc:h2:tcp://localhost/mem:cgv"}) {
            assertThatThrownBy(() -> PaymentDemoDatabaseGuard.requireInMemoryH2(dataSource(url)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("The payment-demo profile requires an isolated H2 in-memory database");
        }
    }

    private DataSource dataSource(String url) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(url);
        return dataSource;
    }
}
