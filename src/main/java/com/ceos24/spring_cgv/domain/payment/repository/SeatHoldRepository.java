package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.dto.SeatPosition;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SeatHoldRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    // 사용자가 결제창에서 결제를 마칠 때까지 좌석을 잡아두는 시간 -> 5분
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    // 락 획득을 기다리는 시간. -> 3ms
    private static final long LOCK_WAIT_SECONDS = 3;

    /***
     * 함수 기능: 선택한 좌석을 결제가 끝날 때까지 선점한다.
     * @param screeningId 상영회차 식별자
     * @param seats 선점할 좌석 좌표 목록
     * @param orderId 선점의 주인을 구분하는 주문 번호
     * @return 요청한 좌석을 모두 선점했으면 true, 하나라도 이미 선점되어 있으면 false
     */
    public boolean hold(Long screeningId, List<SeatPosition> seats, String orderId) {

        // MultiLock을 통해 각 좌석의 락을 전부 획득하거나, 하나라도 실패하면 이미 잡은 것을 풀고 실패한다.
        RLock multiLock = redissonClient.getMultiLock(seats.stream()
                .map(seat -> redissonClient.getLock(lockKey(screeningId, seat)))
                .toArray(RLock[]::new));

        boolean locked = false;

        try {
            // LCOK_WAIT_SECONDS 동안 기다려도 못 잡으면 false
            locked = multiLock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);

            if (!locked) {
                log.info("[좌석 선점 실패] 다른 요청이 같은 좌석을 처리 중입니다. screeningId={}", screeningId);
                return false;
            }

            List<String> keys = seats.stream()
                    .map(seat -> key(screeningId, seat))
                    .toList();

            // 모든 선점 키를 조회한다. SETNX
            List<String> holders = redisTemplate.opsForValue().multiGet(keys);

            // 하나라도 값이 있으면 이미 선점된 좌석이 있다는 뜻.
            if (holders != null && holders.stream().anyMatch(Objects::nonNull)) {
                log.info("[좌석 선점 실패] 이미 선점된 좌석이 있습니다. screeningId={}", screeningId);
                return false;
            }

            // 실제 선점을 진행한다.
            keys.forEach(holdKey -> redisTemplate.opsForValue().set(holdKey, orderId, HOLD_TTL));

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[좌석 선점 중단] 락 대기 중 인터럽트. screeningId={}", screeningId, e);
            throw new PaymentException(PaymentErrorCode.PAYMENT_STORE_UNAVAILABLE);

        } catch (DataAccessException | RedisException e) {
            log.error("[좌석 선점 중단] Redis 장애로 결제를 진행할 수 없습니다. screeningId={}", screeningId, e);
            throw new PaymentException(PaymentErrorCode.PAYMENT_STORE_UNAVAILABLE);

        } finally {
            if (locked) {
                try {
                    multiLock.unlock();
                } catch (RedisException e) {
                    log.error("[좌석 락 해제 실패] screeningId={}", screeningId, e);
                }
            }
        }
    }

    /***
     * 함수 기능: 선점한 좌석을 해제한다. 결제 실패와 결제 취소에서 사용.
     * @param screeningId 상영회차 식별자
     * @param seats 해제할 좌석 좌표 목록
     */
    public void release(Long screeningId, List<SeatPosition> seats) {

        try {
            redisTemplate.delete(seats.stream()
                    .map(seat -> key(screeningId, seat))
                    .toList());

        } catch (DataAccessException e) {
            log.error("[좌석 선점 해제 실패] TTL 만료를 기다립니다. screeningId={}", screeningId, e);
        }
    }

    /***
     * 함수 기능: 좌석 하나에 대응하는 Redis 키를 만든다.
     * @param screeningId 상영회차 식별자
     * @param seat 좌석 좌표
     * @return hold:{상영회차}:{행}:{열} 형식의 키
     */
    private String key(Long screeningId, SeatPosition seat) {
        return "hold:" + screeningId + ":" + seat.seatRow() + ":" + seat.seatNumber();
    }

    /***
     * 함수 기능: 좌석 하나에 대응하는 락 키를 만든다. -> multiLock용
     * @param screeningId 상영회차 식별자
     * @param seat 좌석 좌표
     * @return seatlock:{상영회차}:{행}:{열} 형식의 키
     */
    private String lockKey(Long screeningId, SeatPosition seat) {
        return "seatlock:" + screeningId + ":" + seat.seatRow() + ":" + seat.seatNumber();
    }
}