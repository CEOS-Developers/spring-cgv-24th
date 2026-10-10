package com.ceos24.cgv.support;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 동시 요청을 "둘 다 읽은 뒤에 쓴다"는 순서로 고정해, 경합이 우연이 아니라 매번 일어나게 한다.
// 걸어 둔 저장소 메서드가 반환된 직후 요청들이 서로를 기다린다(RaceGateConfig가 부른다).
//
// 제어가 있으면 늦은 요청은 DB 락에 막혀 게이트에 오지 못한다. 앞선 요청이 그 요청을 끝없이 기다리면
// JVM의 대기와 DB의 대기가 서로를 무는 교착이 되므로, 정해진 시간이 지나면 혼자 지나간다.
// 이 시간은 락 대기 한도(3초)보다 짧아야 늦은 요청이 락 예외 없이 앞선 커밋을 기다릴 수 있다.
// 결과에는 영향을 주지 않는다. 제어가 없으면 모두 막힘 없이 도착해 함께 지나가고, 있으면 하나씩 지나간다.
public final class RaceGate {

    private static final Duration ALONE_AFTER = Duration.ofSeconds(1);
    private static final Map<String, RaceGate> ARMED = new ConcurrentHashMap<>();

    private final CountDownLatch arrivals;
    private final AtomicInteger aloneCount = new AtomicInteger();

    private RaceGate(int parties) {
        this.arrivals = new CountDownLatch(parties);
    }

    public static RaceGate arm(Class<?> repository, String methodName, int parties) {
        RaceGate gate = new RaceGate(parties);
        ARMED.put(keyOf(repository, methodName), gate);
        return gate;
    }

    public static void disarmAll() {
        ARMED.clear();
    }

    static void passed(Method method) {
        RaceGate gate = ARMED.get(keyOf(method.getDeclaringClass(), method.getName()));
        if (gate != null) {
            gate.arrive();
        }
    }

    // 상대를 기다리지 못하고 혼자 지나간 요청 수. 0이면 경합 구간이 실제로 겹쳤다.
    public int aloneCount() {
        return aloneCount.get();
    }

    private void arrive() {
        arrivals.countDown();
        try {
            if (!arrivals.await(ALONE_AFTER.toMillis(), TimeUnit.MILLISECONDS)) {
                aloneCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String keyOf(Class<?> type, String methodName) {
        return type.getName() + "#" + methodName;
    }
}
