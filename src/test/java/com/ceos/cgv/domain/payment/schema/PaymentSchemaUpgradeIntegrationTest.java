package com.ceos.cgv.domain.payment.schema;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.support.PaymentIntegrationFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Changes only the create/drop schema belonging to the non-reusable test container. */
@SpringBootTest
@EnabledIfSystemProperty(named = "cgv.test.database", matches = "mysql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentSchemaUpgradeIntegrationTest extends PaymentIntegrationFixture {
    @ParameterizedTest
    @ValueSource(strings = {"'HELD','RESERVED','EXPIRED','RELEASED','CANCELED'",
            "'CANCELED','EXPIRED','HELD','RELEASED','RESERVED'"})
    void 기존상태와_좌석이력을_보존하며_수동SQL을_적용하고_JPA읽기쓰기와_제약을_검증한다(String baselineStates) throws Exception {
        // Establish a precise week-3 baseline from the generated existing domain schema.
        // Never use foreign_key_checks=0: dependency-ordered drops affect only new, empty tables.
        jdbc.execute("DROP TABLE payment_operation");
        jdbc.execute("DROP TABLE payment");
        jdbc.execute("DROP TABLE payment_order_seat");
        jdbc.execute("DROP TABLE payment_order");
        jdbc.execute("ALTER TABLE reserved_seats DROP COLUMN canceled_at");
        jdbc.execute("ALTER TABLE reservations MODIFY status ENUM(" + baselineStates + ") NOT NULL");
        String[] oldStates = {"HELD", "RESERVED", "EXPIRED", "RELEASED", "CANCELED"};
        for (int index = 0; index < oldStates.length; index++) {
            jdbc.update("INSERT INTO reservations(reservation_id,user_id,screening_id,status,request_key,created_at,updated_at)"
                            + " VALUES(?,8911,8915,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    9001 + index, oldStates[index], UUID.randomUUID().toString());
            jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number) VALUES(?,?,'A',1)",
                    9101 + index, 9001 + index);
        }
        List<Map<String, Object>> reservationsBefore = jdbc.queryForList("SELECT * FROM reservations ORDER BY reservation_id");
        List<Map<String, Object>> seatsBefore = jdbc.queryForList("SELECT * FROM reserved_seats ORDER BY reserved_seat_id");
        String enumBefore = jdbc.queryForObject("SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE()"
                + " AND table_name='reservations' AND column_name='status'", String.class);
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/week-04-payment.sql"));
        }
        assertThat(jdbc.queryForList("SELECT * FROM reservations ORDER BY reservation_id")).isEqualTo(reservationsBefore);
        assertThat(jdbc.queryForList("SELECT reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id"
                + " FROM reserved_seats ORDER BY reserved_seat_id")).isEqualTo(seatsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserved_seats WHERE canceled_at IS NULL", Integer.class)).isEqualTo(5);
        String enumAfter = jdbc.queryForObject("SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE()"
                + " AND table_name='reservations' AND column_name='status'", String.class);
        assertThat(enumAfter).isEqualTo(enumBefore.substring(0, enumBefore.length() - 1) + ",'PAYMENT_PENDING')");
        jdbc.update("UPDATE reservations SET status='PAYMENT_PENDING' WHERE reservation_id=9001");
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=9001", String.class)).isEqualTo("PAYMENT_PENDING");
        // Old rows remain snapshots, free from active seat occupancy in this baseline.
        jdbc.update("UPDATE reservations SET status='RELEASED' WHERE reservation_id=9001");
        PaymentOrderResponse order = order(2, 3);
        GatewayPayment pg = done(order);
        PaymentOperationResponse approved = approve(order);
        assertThat(approved.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(queries.order(USER_ID, order.orderId()).seats()).hasSize(2);
        GatewayPayment cancellation = canceled(pg, 10000);
        when(gateway.cancel(any())).thenAnswer(invocation -> { assertOutsideTransaction(); return cancellation; });
        assertThat(service.cancel(USER_ID, order.paymentId(), UUID.randomUUID(),
                new PaymentCancelRequest(List.of(order.seats().getFirst().reservedSeatId()), "스키마 검증"))
                .result().balanceAmount()).isEqualTo(10000);
        assertThat(canceledSeats(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE reservation_id BETWEEN 9001 AND 9005", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserved_seats WHERE reserved_seat_id BETWEEN 9101 AND 9105", Integer.class)).isEqualTo(5);
        verifyConstraints(order, approved.operationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
                + " AND table_name IN('payment_order','payment_order_seat','payment','payment_operation') AND engine='InnoDB'", Integer.class)).isEqualTo(4);
        System.out.printf("Payment schema upgrade: engine=MySQL version=%s preserved_reservations=5 preserved_reserved_seats=5 nullable_defaults=5 payment_tables=4 jpa_order_approval_partial_cancel=PASS unique_fk_checks=PASS snapshot_scalar_fk=0 baseline_enum=%s%n",
                jdbc.queryForObject("SELECT VERSION()", String.class), enumBefore);
    }

    private void verifyConstraints(PaymentOrderResponse order, Long operationId) {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_order(created_at,updated_at,order_id,reservation_id,user_id,order_name,seat_price,total_amount,expires_at)"
                + " SELECT created_at,updated_at,order_id,999999,user_id,order_name,seat_price,total_amount,expires_at FROM payment_order WHERE id=?",
                jdbc.queryForObject("SELECT payment_order_id FROM payment WHERE id=?", Long.class, order.paymentId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_order(created_at,updated_at,order_id,reservation_id,user_id,order_name,seat_price,total_amount,expires_at)"
                + " SELECT created_at,updated_at,'different-order',reservation_id,user_id,order_name,seat_price,total_amount,expires_at FROM payment_order"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment(created_at,updated_at,payment_order_id,status,balance_amount)"
                + " VALUES(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,999999,'READY',10000)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment(created_at,updated_at,payment_order_id,status,balance_amount)"
                + " SELECT created_at,updated_at,payment_order_id,status,balance_amount FROM payment WHERE id=?", order.paymentId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_order_seat(payment_order_id,seat_index,reserved_seat_id,seat_row,seat_number)"
                + " VALUES(999999,0,12345,'A',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_order_seat SELECT * FROM payment_order_seat LIMIT 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_operation SET request_key=(SELECT request_key FROM (SELECT request_key FROM payment_operation WHERE id="
                + operationId + ") original), type='CONFIRM' WHERE id<>?", operationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_operation SET payment_id=999999 WHERE id=?", operationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_operation SET gateway_key=(SELECT gateway_key FROM (SELECT gateway_key FROM payment_operation WHERE id="
                + operationId + ") original) WHERE id<>?", operationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Nullable unique values allow multiple unbound/unused slots, as required by READY orders.
        jdbc.update("INSERT INTO payment_order(created_at,updated_at,order_id,reservation_id,user_id,order_name,seat_price,total_amount,expires_at)"
                + " VALUES(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'scalar-snapshot',999999,999999,'snapshot',10000,10000,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO payment_order(created_at,updated_at,order_id,reservation_id,user_id,order_name,seat_price,total_amount,expires_at)"
                + " VALUES(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'scalar-snapshot-two',999998,999998,'snapshot',10000,10000,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO payment(created_at,updated_at,payment_order_id,status,balance_amount)"
                + " SELECT CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,id,'READY',10000 FROM payment_order WHERE order_id='scalar-snapshot-two'");
        Long extraOrderId = jdbc.queryForObject("SELECT id FROM payment_order WHERE order_id='scalar-snapshot'", Long.class);
        jdbc.update("INSERT INTO payment(created_at,updated_at,payment_order_id,status,balance_amount) VALUES(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'READY',10000)", extraOrderId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE payment_key IS NULL", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment SET payment_key=(SELECT payment_key FROM (SELECT payment_key FROM payment WHERE id="
                + order.paymentId() + ") original) WHERE payment_order_id=?", extraOrderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE payment_operation SET compensation_key='schema-compensation' WHERE id=?", operationId);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_operation SET compensation_key='schema-compensation' WHERE id<>?", operationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE()"
                + " AND table_name IN('payment_order','payment_order_seat','payment')"
                + " AND column_name IN('user_id','reservation_id','reserved_seat_id','active_operation_id') AND referenced_table_name IS NOT NULL", Integer.class)).isZero();
    }
}
