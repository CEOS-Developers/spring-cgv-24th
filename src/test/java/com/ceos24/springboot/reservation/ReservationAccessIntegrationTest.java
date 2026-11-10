package com.ceos24.springboot.reservation;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationSeat;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.dto.SeatResponse;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Screen;
import com.ceos24.springboot.theater.domain.ScreenType;
import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.ScreeningType;
import com.ceos24.springboot.theater.domain.ScreeningSeat;
import com.ceos24.springboot.theater.domain.Seat;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:reservation-access-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationAccessIntegrationTest {
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final String PASSWORD = "Test1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private String emptyUserToken;
    private Screening screening;
    private Seat oldSeat;
    private Seat newSeatOne;
    private Seat newSeatTwo;
    private Seat otherUserSeat;
    private Seat availableSeat;
    private Reservation olderA;
    private Reservation newerA;
    private Reservation reservationB;

    @BeforeEach
    void setUp() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            String encodedPassword = passwordEncoder.encode(PASSWORD);
            userA = persist(User.create("예매자A", "reservation-a@example.com",
                    encodedPassword, "010-1111-1111"));
            userB = persist(User.create("예매자B", "reservation-b@example.com",
                    encodedPassword, "010-2222-2222"));
            persist(User.create("예매없음", "reservation-empty@example.com",
                    encodedPassword, "010-3333-3333"));

            Theater theater = persist(Theater.create("테스트극장", Region.Seoul));
            Screen screen = persist(Screen.create(
                    theater, "1관", ScreenType.General, 1, 5
            ));
            Movie movie = persist(Movie.builder()
                    .titleKr("예매테스트영화")
                    .runtimeMinutes(120)
                    .ageRating(AgeRating.ALL)
                    .build());
            screening = persist(Screening.builder()
                    .screen(screen)
                    .movie(movie)
                    .screeningDate(LocalDate.now(clock).plusDays(7))
                    .startTime(LocalTime.NOON)
                    .endTime(LocalTime.of(14, 0))
                    .screeningType(ScreeningType.Original)
                    .build());

            oldSeat = persist(Seat.create(screen, "A", 1));
            newSeatOne = persist(Seat.create(screen, "A", 2));
            newSeatTwo = persist(Seat.create(screen, "A", 3));
            otherUserSeat = persist(Seat.create(screen, "A", 4));
            availableSeat = persist(Seat.create(screen, "A", 5));
            for (Seat seat : List.of(oldSeat, newSeatOne, newSeatTwo, otherUserSeat, availableSeat)) {
                persist(ScreeningSeat.create(screening, seat));
            }

            LocalDateTime base = LocalDateTime.now(clock).minusDays(2);
            olderA = createReservation(userA, base, List.of(oldSeat));
            reservationB = createReservation(userB, base.plusDays(1), List.of(otherUserSeat));
            newerA = createReservation(userA, base.plusDays(2), List.of(newSeatOne, newSeatTwo));
            // This reservation is unpaid: only Pending reservations can be canceled.
            jdbcTemplate.update("update reservation set status = 'Pending', expires_at = ? where reservation_id = ?",
                    LocalDateTime.now(clock).plusMinutes(10), newerA.getReservationId());
            entityManager.flush();

            String dayType = screening.getScreeningDate().getDayOfWeek().getValue() >= 6
                    ? "Weekend" : "Weekday";
            jdbcTemplate.update("""
                    insert into price
                        (audience_type, day_type, time_type, screen_category, ticket_price)
                    values (?, ?, ?, ?, ?)
                    """, "Adult", dayType, "Normal", "General", 10000);
            entityManager.clear();

        });

        tokenA = login("reservation-a@example.com");
        tokenB = login("reservation-b@example.com");
        emptyUserToken = login("reservation-empty@example.com");
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            for (String table : List.of("reservation_seat", "reservation", "screening_seat",
                    "screening", "seat", "screen", "theater", "movie", "cgv_user", "price")) {
                jdbcTemplate.update("delete from " + table);
            }
        });
    }

    @Test
    void listContainsOnlyOwnReservationsNewestFirstWithCorrectSeats() throws Exception {
        List<ReservationResponse> responses = getList(tokenA);

        assertThat(responses).extracting(ReservationResponse::reservationId)
                .containsExactly(newerA.getReservationId(), olderA.getReservationId());
        assertThat(responses.get(0).seats()).extracting(SeatResponse::seatId)
                .containsExactlyInAnyOrder(newSeatOne.getSeatId(), newSeatTwo.getSeatId());
        assertThat(responses.get(1).seats()).extracting(SeatResponse::seatId)
                .containsExactly(oldSeat.getSeatId());
    }

    @Test
    void otherUserSeesOnlyTheirOwnReservationAndSeat() throws Exception {
        List<ReservationResponse> responses = getList(tokenB);

        assertThat(responses).extracting(ReservationResponse::reservationId)
                .containsExactly(reservationB.getReservationId());
        assertThat(responses.get(0).seats()).extracting(SeatResponse::seatId)
                .containsExactly(otherUserSeat.getSeatId());
    }

    @Test
    void userWithoutReservationsReceivesEmptyList() throws Exception {
        assertThat(getList(emptyUserToken)).isEmpty();
    }

    @Test
    void userIdQueryParameterCannotChangeReservationOwnerFilter() throws Exception {
        String body = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("userId", userB.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonMapper.readValue(body, ReservationResponse[].class))
                .extracting(ReservationResponse::reservationId)
                .containsExactly(newerA.getReservationId(), olderA.getReservationId());
    }

    @Test
    void ownerCanReadBothReservationsByTheirReservationIds() throws Exception {
        // Two distinct reservation IDs detect accidentally swapped userId/reservationId.
        for (Reservation reservation : List.of(olderA, newerA)) {
            String body = mockMvc.perform(get("/api/reservations/{id}",
                            reservation.getReservationId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(jsonMapper.readValue(body, ReservationResponse.class).reservationId())
                    .isEqualTo(reservation.getReservationId());
        }
    }

    @Test
    void usersCannotReadEachOthersReservation() throws Exception {
        mockMvc.perform(get("/api/reservations/{id}", newerA.getReservationId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

        mockMvc.perform(get("/api/reservations/{id}", reservationB.getReservationId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void missingReservationAlsoReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/reservations/{id}", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void ownerCanCancelAndOnlyTheirSeatsAreReleased() throws Exception {
        mockMvc.perform(patch("/api/reservations/{id}/cancel", newerA.getReservationId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Canceled"));

        assertThat(reservationRepository.findById(newerA.getReservationId())
                .orElseThrow().getStatus()).isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeatRepository
                .findAllByReservation_ReservationId(newerA.getReservationId())).isEmpty();
        assertThat(reservationRepository.findById(reservationB.getReservationId())
                .orElseThrow().getStatus()).isEqualTo(ReservationStatus.Reserved);
        assertThat(reservationSeatRepository
                .findAllByReservation_ReservationId(reservationB.getReservationId()))
                .extracting(reservationSeat -> reservationSeat.getSeat().getSeatId())
                .containsExactly(otherUserSeat.getSeatId());
    }

    @Test
    void otherUserCannotCancelAndReservationAndSeatsRemainUnchanged() throws Exception {
        mockMvc.perform(patch("/api/reservations/{id}/cancel", newerA.getReservationId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

        assertThat(reservationRepository.findById(newerA.getReservationId())
                .orElseThrow().getStatus()).isEqualTo(ReservationStatus.Pending);
        assertThat(reservationSeatRepository
                .findAllByReservation_ReservationId(newerA.getReservationId()))
                .extracting(reservationSeat -> reservationSeat.getSeat().getSeatId())
                .containsExactlyInAnyOrder(newSeatOne.getSeatId(), newSeatTwo.getSeatId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIST", "DETAIL", "CANCEL", "CREATE"})
    void anonymousRequestsAreRejected(String operation) throws Exception {
        MockHttpServletRequestBuilder request = switch (operation) {
            case "LIST" -> get("/api/reservations");
            case "DETAIL" -> get("/api/reservations/{id}", newerA.getReservationId());
            case "CANCEL" -> patch("/api/reservations/{id}/cancel", newerA.getReservationId());
            case "CREATE" -> post("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createRequest()));
            default -> throw new IllegalArgumentException(operation);
        };

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    @Test
    void createdReservationBelongsToAuthenticatedUserAndIsHiddenFromOthers() throws Exception {
        String body = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ReservationResponse response = jsonMapper.readValue(body, ReservationResponse.class);

        Reservation saved = reservationRepository.findById(response.reservationId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(userB.getId());
        assertThat(saved.getTotalPrice()).isEqualTo(10000);
        assertThat(response.seats()).extracting(SeatResponse::seatId)
                .containsExactly(availableSeat.getSeatId());
        assertThat(getList(tokenB)).extracting(ReservationResponse::reservationId)
                .contains(response.reservationId());
        assertThat(getList(tokenA)).extracting(ReservationResponse::reservationId)
                .doesNotContain(response.reservationId());
        mockMvc.perform(get("/api/reservations/{id}", response.reservationId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    private ReservationCreateRequest createRequest() {
        return new ReservationCreateRequest(
                screening.getScreeningId(), 0, 0, 1, 0, List.of(availableSeat.getSeatId())
        );
    }

    private Reservation createReservation(User user, LocalDateTime at, List<Seat> seats) {
        Reservation reservation = persist(Reservation.builder()
                .user(user)
                .screening(screening)
                .childCount(0)
                .youthCount(0)
                .adultCount(seats.size())
                .seniorCount(0)
                .totalPrice(seats.size() * 10000)
                .status(ReservationStatus.Reserved)
                .reservationAt(at)
                .build());
        seats.forEach(seat -> persist(ReservationSeat.create(reservation, screening, seat)));
        return reservation;
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private List<ReservationResponse> getList(String token) throws Exception {
        String body = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Arrays.asList(jsonMapper.readValue(body, ReservationResponse[].class));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(body, LoginResponse.class).accessToken();
    }
}
