package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 부하 테스트 시드 SQL도 엔티티가 바뀌면 컴파일 오류 없이 깨진다. 깨진 채로 부하를 걸면 기동 실패나
// 로그인 실패가 "앱이 못 버틴다"로 보일 수 있어, 운영과 같은 MySQL에서 실제 스키마에 넣어 본다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class LoadtestDataTest {

    @Autowired TransactionTemplate transactionTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ReservationSeatRepository reservationSeatRepository;

    @PersistenceContext EntityManager em;

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from ReservationSeat").executeUpdate();
            em.createQuery("delete from Reservation").executeUpdate();
            em.createQuery("delete from User u where u.loginId like 'loadtest%'").executeUpdate();
            em.createQuery("delete from Screening").executeUpdate();
            em.createQuery("delete from Theater").executeUpdate();
            em.createQuery("delete from Movie").executeUpdate();
            em.createQuery("delete from Branch").executeUpdate();
        });
    }

    @Test
    @Sql("classpath:db/loadtest-data.sql")
    void 부하_시드가_현재_스키마에_들어가고_시나리오가_기대하는_상태다() {
        transactionTemplate.executeWithoutResult(status -> {
            Long users = em.createQuery("select count(u) from User u where u.loginId like 'loadtest%'", Long.class)
                    .getSingleResult();
            assertThat(users).isEqualTo(1000);

            for (String loginId : List.of("loadtest0001", "loadtest1000")) {
                User user = em.createQuery("select u from User u where u.loginId = :id", User.class)
                        .setParameter("id", loginId).getSingleResult();
                assertThat(passwordEncoder.matches("loadtest1!", user.getPassword())).isTrue();
            }

            // 지난 회차는 선점할 수 없다. 부팅 시점 기준 "내일"이어야 자정을 넘겨도 미래다
            List<Screening> screenings = em.createQuery(
                    "select s from Screening s join fetch s.theater", Screening.class).getResultList();
            assertThat(screenings).hasSize(101)
                    .allSatisfy(s -> assertThat(s.getStartAt()).isAfter(LocalDateTime.now()))
                    .allSatisfy(s -> assertThat(s.getTheater().getTheaterType()).isEqualTo(TheaterType.IMAX));

            assertThat(reservationSeatRepository.findOccupiedPositionsByScreeningId(
                    2001L, ReservationStatus.PENDING, LocalDateTime.now())).hasSize(132);
        });
    }
}
