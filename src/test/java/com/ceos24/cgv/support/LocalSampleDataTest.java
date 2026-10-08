package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.store.entity.Stock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// local 프로필의 샘플 데이터 SQL은 엔티티가 바뀌어도 컴파일 오류가 나지 않는다. 컬럼이 어긋나면
// local 기동이 실패해서야 알게 되므로, 운영과 같은 MySQL에서 실제 스키마에 넣어 본다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class LocalSampleDataTest {

    @Autowired TransactionTemplate transactionTemplate;

    @PersistenceContext EntityManager em;

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from Stock").executeUpdate();
            em.createQuery("delete from Product").executeUpdate();
            em.createQuery("delete from Screening").executeUpdate();
            em.createQuery("delete from Movie").executeUpdate();
            em.createQuery("delete from Theater").executeUpdate();
            em.createQuery("delete from Branch").executeUpdate();
        });
    }

    @Test
    @Sql("classpath:db/local-sample-data.sql")
    void 샘플_데이터가_현재_스키마에_들어가고_결제_테스트에_쓸_수_있는_상태다() {
        transactionTemplate.executeWithoutResult(status -> {
            // 지난 회차는 선점할 수 없다. 회차는 부팅 시점 기준 "내일"이어야 한다
            List<Screening> screenings = em.createQuery("select s from Screening s", Screening.class).getResultList();
            assertThat(screenings).hasSize(2)
                    .allSatisfy(s -> assertThat(s.getStartAt()).isAfter(LocalDateTime.now()));

            List<Stock> stocks = em.createQuery(
                    "select s from Stock s join fetch s.product join fetch s.branch order by s.id", Stock.class)
                    .getResultList();
            assertThat(stocks).hasSize(3);
            assertThat(stocks.getFirst().getBranch().isOperating()).isTrue();
            assertThat(stocks).filteredOn(Stock::isSoldOut)
                    .singleElement()
                    .satisfies(s -> assertThat(s.getProduct().getName()).isEqualTo("나초"));
        });
    }
}
