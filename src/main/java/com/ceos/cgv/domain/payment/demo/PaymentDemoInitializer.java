package com.ceos.cgv.domain.payment.demo;

import com.ceos.cgv.domain.auth.dto.SignupRequest;
import com.ceos.cgv.domain.auth.service.RegistrationService;
import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.enums.ScreenType;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.domain.cinema.repository.ScreenRepository;
import com.ceos.cgv.domain.movie.dto.ScreeningCreateRequest;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.service.ScreeningService;
import com.ceos.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Profile("payment-demo")
@RequiredArgsConstructor
public class PaymentDemoInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;
    private final MovieRepository movieRepository;
    private final ScreeningService screeningService;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        PaymentDemoDatabaseGuard.requireInMemoryH2(dataSource);
        if (userRepository.existsByLoginId("paymentdemo")) {
            return;
        }
        registrationService.register(new SignupRequest(
                "paymentdemo", "결제 테스트 회원", "payment-demo@example.invalid", "DemoPayment123!"));
        Cinema cinema = cinemaRepository.save(Cinema.builder()
                .name("결제 테스트 영화관").address("임시 데모 데이터").build());
        Screen screen = screenRepository.save(Screen.builder()
                .cinema(cinema).screenType(ScreenType.GENERAL).rowCount(3).seatsPerRow(4).build());
        Movie movie = movieRepository.save(Movie.builder()
                .title("결제 테스트 상영").description("테스트 결제와 좌석 취소를 확인하는 임시 영화")
                .runningTime(100).releaseDate(LocalDate.now(clock)).ageRating(AgeRating.ALL).build());
        screeningService.create(new ScreeningCreateRequest(
                movie.getId(), screen.getId(), LocalDateTime.now(clock).plusDays(2)));
    }
}
