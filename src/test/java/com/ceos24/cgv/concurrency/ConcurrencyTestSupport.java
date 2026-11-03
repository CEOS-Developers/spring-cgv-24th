package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.entity.Stock;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderRepository;
import com.ceos24.cgv.domain.concession.repository.StockRepository;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.schedule.repository.ScheduleRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.ScreenTypeRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
abstract class ConcurrencyTestSupport {

    @MockitoBean
    protected TossPaymentClient tossPaymentClient;

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected MovieRepository movieRepository;
    @Autowired
    protected TheaterRepository theaterRepository;
    @Autowired
    protected ScreenTypeRepository screenTypeRepository;
    @Autowired
    protected ScreenRepository screenRepository;
    @Autowired
    protected SeatRepository seatRepository;
    @Autowired
    protected ScheduleRepository scheduleRepository;
    @Autowired
    protected ReservationRepository reservationRepository;
    @Autowired
    protected ReservationSeatRepository reservationSeatRepository;
    @Autowired
    protected ItemRepository itemRepository;
    @Autowired
    protected StockRepository stockRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected OrderItemRepository orderItemRepository;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected PaymentCancelRepository paymentCancelRepository;

    @AfterEach
    void cleanUp() {
        paymentCancelRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        reservationSeatRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        scheduleRepository.deleteAllInBatch();
        screenRepository.deleteAllInBatch();
        screenTypeRepository.deleteAllInBatch();
        theaterRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected User saveUser() {
        return userRepository.save(User.create("user-" + UUID.randomUUID(), "password", "사용자", null));
    }

    protected Theater saveTheater() {
        return theaterRepository.save(Theater.create("CGV 강남", "서울", "강남구", "설명", null));
    }

    protected Item saveItem(int price) {
        return itemRepository.save(Item.create("팝콘", price));
    }

    protected Stock saveStock(Theater theater, Item item, int quantity) {
        return stockRepository.save(Stock.create(theater, item, quantity));
    }

    protected Order saveOrder(User user, Theater theater, Item item, int quantity) {
        Order order = orderRepository.save(Order.create(user, theater, item.getPrice() * quantity));
        orderItemRepository.save(OrderItem.create(order, item, quantity, item.getPrice()));
        return order;
    }

    protected Schedule saveSchedule(int seatCount) {
        Movie movie = movieRepository.save(Movie.create("테스트 영화", "드라마", 120, "영화 설명", AgeRating.FIFTEEN,
                MovieStatus.SHOWING, LocalDate.of(2026, 9, 1), null));
        ScreenType screenType = screenTypeRepository.save(ScreenType.create("일반관", 10, 10));
        Screen screen = screenRepository.save(Screen.create(saveTheater(), screenType, "1관"));
        List<Seat> seats = new ArrayList<>();
        for (int col = 1; col <= seatCount; col++) {
            seats.add(Seat.create(screen, 1, col));
        }
        seatRepository.saveAll(seats);
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        return scheduleRepository.save(Schedule.create(movie, screen, startTime, startTime.plusHours(2), 15_000));
    }

    protected TossPaymentResponse tossResponse(String status, Payment payment, int balanceAmount, int cancelAmount) {
        OffsetDateTime now = OffsetDateTime.now();
        List<TossPaymentResponse.Cancel> cancels = cancelAmount == 0 ? List.of()
                : List.of(new TossPaymentResponse.Cancel("tx-" + UUID.randomUUID(), cancelAmount, "취소", now));
        return new TossPaymentResponse("pk-" + payment.getId(), payment.getTossOrderId(), status, payment.getAmount(),
                balanceAmount, "카드", now, cancels);
    }

    // 모든 스레드를 동시에 출발시키고, 작업별 결과(성공이면 null, 실패면 예외)를 작업 순서대로 반환한다
    protected List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
        int size = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(size);
        CountDownLatch ready = new CountDownLatch(size);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(size);
        Throwable[] failures = new Throwable[size];
        for (int i = 0; i < size; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    tasks.get(index).run();
                } catch (Throwable e) {
                    failures[index] = e;
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();
        if (!finished) {
            throw new IllegalStateException("동시 작업이 60초 안에 끝나지 않았습니다.");
        }
        return Arrays.asList(failures);
    }

    protected static long successCount(List<Throwable> results) {
        return results.stream().filter(Objects::isNull).count();
    }

    protected static long failureCount(List<Throwable> results, String code) {
        return results.stream().filter(Objects::nonNull).filter(e -> code.equals(errorCode(e))).count();
    }

    protected static Map<String, Long> failureCounts(List<Throwable> results) {
        return results.stream().filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ConcurrencyTestSupport::errorCode, Collectors.counting()));
    }

    protected static String errorCode(Throwable e) {
        return e instanceof GeneralException general ? general.getErrorReason().getCode() : e.getClass().getSimpleName();
    }
}
