package com.ceos.cgv.domain.payment.demo;

import com.ceos.cgv.domain.auth.dto.LoginRequest;
import com.ceos.cgv.domain.auth.service.LoginService;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("payment-demo")
class PaymentDemoInitializerTest {
    @Autowired
    private PaymentDemoInitializer initializer;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ScreeningRepository screeningRepository;
    @Autowired
    private ScreeningSeatRepository screeningSeatRepository;
    @Autowired
    private LoginService loginService;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private Clock clock;

    @Test
    void 격리된_임시_DB에_로그인_가능한_회원과_미래_상영을_한번만_생성한다() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:payment-demo-");
        }
        initializer.run(new DefaultApplicationArguments());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(movieRepository.count()).isEqualTo(1);
        assertThat(screeningRepository.count()).isEqualTo(1);
        assertThat(screeningSeatRepository.count()).isEqualTo(12);
        assertThat(screeningRepository.findAll().getFirst().getStartAt()).isAfter(LocalDateTime.now(clock));
        assertThat(loginService.login(new LoginRequest("paymentdemo", "DemoPayment123!" )).accessToken()).isNotBlank();
        assertThat(userRepository.findByLoginId("paymentdemo").orElseThrow().getPasswordHash())
                .isNotEqualTo("DemoPayment123!");
    }
}
