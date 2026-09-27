package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@ConditionalOnProperty(name = "cgv.seat-backfill.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ScreeningSeatBackfillRunner implements ApplicationRunner {
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatBackfillService backfillService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        if (applicationContext instanceof WebApplicationContext) {
            throw new IllegalStateException(
                    "Seat backfill must run with --spring.main.web-application-type=none");
        }
        int migrated = 0;
        for (Long screeningId : screeningRepository.findAllIdsOrderById()) {
            ScreeningSeatBackfillService.BackfillResult result = backfillService.backfill(screeningId);
            if (result.seatsCreated() > 0 || result.historiesLinked() > 0 || result.occupantsLinked() > 0
                    || result.reservationsExpired() > 0) {
                migrated++;
                log.info("Backfilled screening {}: {} seats, {} histories, {} occupants, {} expired holds",
                        screeningId, result.seatsCreated(), result.historiesLinked(), result.occupantsLinked(),
                        result.reservationsExpired());
            }
        }
        log.info("Screening seat backfill finished: {} screenings changed", migrated);
    }
}
