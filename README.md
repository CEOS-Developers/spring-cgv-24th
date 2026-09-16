# spring-cgv-24th
CEOS 24기 백엔드 스터디 - CGV 클론 코딩 프로젝트

# CGV 클론코딩

## 서비스 소개

CGV 서비스를 기반으로 백엔드 핵심 기능을 구현한 프로젝트입니다.

- 영화관 조회 및 찜
- 영화 조회 및 찜
- 영화 예매 및 취소
- 매점 상품 구매
- 영화관 / 상영관 / 좌석 / 예매 관계 직접 모델링
- JPA 연관관계, 예외 처리, 서비스 단위 테스트 적용

단순 기능 구현에 그치지 않고, 실제 서비스라면 데이터 관계를 어떻게 구성할지 고민하며 설계했습니다.

---

## 구현 기능

1. 영화관 조회
2. 영화관 찜
3. 영화 조회
4. 영화 예매 / 취소
5. 영화 찜
6. 매점 상품 구매
    - 환불 기능 제외

---

## 설계하면서 고려한 점

### 1. 상영관과 좌석 구조

모든 영화관에 일반관과 특별관 존재.

같은 종류의 상영관이라면 좌석 구조도 동일하다고 가정했습니다.

초기에는 `Screen`마다 좌석을 직접 관리하려 했지만, 같은 상영관 타입마다 동일한 좌석 구조를 반복해서 저장하게 되는 문제가 있었습니다.

그래서 좌석 배치 정보를 `SeatTemplate`으로 분리했습니다.

```
ScreenType
   ↓
SeatTemplate
   ↓
Seat
```

예를 들어 여러 영화관에 같은 IMAX 상영관이 존재하더라도, 동일한 좌석 구조라면 같은 템플릿 기준으로 관리.

이번 과제에서는 좌석 구조를 다음과 같이 단순화했습니다.

- 직사각형 형태
- 중간에 비어 있는 좌석 없음
- 통로 고려하지 않음

구현하면서 상영관 정보와 좌석 배치 정보를 분리하는 것이 더 자연스럽다는 점을 알게 됐습니다.

---

### 2. 영화관별 매점 관리

각 영화관에는 하나의 매점만 존재한다고 가정했습니다.

별도의 `Store` 엔티티를 만들기보다 영화관별 `SnackStock`으로 재고를 관리했습니다.

```
Theater
   ↓
SnackStock
   ↓
SnackItem
```

모든 영화관의 메뉴는 동일하지만 재고 수량은 다르게 관리.

예시

```
강남 CGV 팝콘 재고 : 20개
홍대 CGV 팝콘 재고 : 8개
```

역할 분리

- `SnackItem` : 상품 정보
- `SnackStock` : 영화관별 상품 재고
- `SnackOrder` : 주문 정보
- `SnackOrderItem` : 주문별 상품 정보

같은 메뉴 정보를 영화관마다 중복 저장하지 않고, 재고만 영화관별로 관리하도록 설계했습니다.

재고는 항상 1개 이상 존재한다고 가정했습니다.

---

# ERD 및 DB 모델링

## 1. ERD

https://www.erdcloud.com/d/2LXYJEwkeevufXZwr

---

## 2. 전체 테이블 구성

### 영화 관련

| Entity | 역할 |
| --- | --- |
| `Movie` | 영화 기본 정보 |
| `MovieStatistic` | 영화 통계 정보 |
| `UserMovie` | 영화 찜 정보 |

### 영화 예매 관련

| Entity | 역할 |
| --- | --- |
| `Reservation` | 영화 예매 정보 |
| `ReservationSeat` | 예매 좌석 정보 |

### 🗓 상영 일정 관련

| Entity | 역할 |
| --- | --- |
| `Screening` | 영화 상영 일정 |

### 매점 구매 관련

| Entity | 역할 |
| --- | --- |
| `SnackItem` | 상품 정보 |
| `SnackOrder` | 주문 정보 |
| `SnackOrderItem` | 주문 상품 정보 |
| `SnackStock` | 영화관별 상품 재고 |

### 극장 관련

| Entity | 역할 |
| --- | --- |
| `Theater` | 영화관 정보 |
| `Screen` | 상영관 정보 |
| `ScreenType` | 일반관 / 특별관 종류 |
| `Seat` | 좌석 정보 |
| `SeatTemplate` | 좌석 배치 템플릿 |
| `UserTheater` | 영화관 찜 정보 |

### 사용자

| Entity | 역할 |
| --- | --- |
| `User` | 사용자 정보 |

---

# 주요 엔티티 관계

### Theater ↔ Screen

```
Theater 1 : N Screen
```

- 하나의 영화관에 여러 상영관 존재
- 하나의 상영관은 하나의 영화관에 소속

---

### ScreenType ↔ Screen

```
ScreenType 1 : N Screen
```

- 하나의 상영관 타입을 여러 Screen이 사용
- 일반관, IMAX 등의 타입 정보 분리

---

### Screen ↔ Seat

```
Screen 1 : N Seat
```

- 하나의 상영관에 여러 좌석 존재
- 각 좌석은 특정 상영관에 소속

---

### SeatTemplate ↔ Seat

```
SeatTemplate 1 : N Seat
```

- 동일한 상영관 타입의 좌석 구조 재사용
- 반복되는 좌석 배치 정보 분리

---

### Movie ↔ Screening

```
Movie 1 : N Screening
```

- 하나의 영화는 여러 시간대에 상영 가능
- 실제 상영 시간은 `Screening`에서 관리

---

### Screen ↔ Screening

```
Screen 1 : N Screening
```

- 하나의 상영관에서 여러 상영 일정 생성 가능
- 어떤 영화가 언제, 어디서 상영되는지 관리

---

### User ↔ Movie

```
User N : M Movie
```

다대다 관계를 `UserMovie`로 분리했습니다.

```
User 1 : N UserMovie
Movie 1 : N UserMovie
```

이유

- `@ManyToMany` 직접 사용하지 않음
- 찜 생성 시간 등 추가 정보 확장 가능
- 관계 자체를 하나의 도메인으로 관리 가능

---

### User ↔ Theater

```
User N : M Theater
```

`UserTheater` 중간 엔티티 사용.

```
User 1 : N UserTheater
Theater 1 : N UserTheater
```

영화 찜과 동일하게 다대다 관계를 중간 엔티티로 풀었습니다.

---

### User ↔ Reservation

```
User 1 : N Reservation
```

- 한 사용자가 여러 번 예매 가능
- 하나의 예매는 한 사용자에 소속

---

### Screening ↔ Reservation

```
Screening 1 : N Reservation
```

- 하나의 상영 일정에 여러 예매 발생 가능
- 영화 자체보다 실제 상영 일정 기준으로 예매

---

### Reservation ↔ ReservationSeat

```
Reservation 1 : N ReservationSeat
```

- 한 번의 예매에서 여러 좌석 선택 가능
- 좌석 단위 중복 예매 검증 가능

---

### SnackItem ↔ SnackStock

```
SnackItem 1 : N SnackStock
```

- 상품 정보는 공통 관리
- 영화관별 재고만 별도 관리

---

### SnackOrder ↔ SnackOrderItem

```
SnackOrder 1 : N SnackOrderItem
```

- 한 주문에 여러 상품 포함 가능
- 상품별 주문 수량 관리

---

# 💡 미션 후 새롭게 알게 된 점

## 1. 비관적 락의 중요성

영화 예매와 매점 구매에서 동시에 여러 요청이 들어올 수 있다는 점을 고려했습니다.

예를 들어 재고가 1개 남았을 때

```
A 사용자 → 재고 1 확인
B 사용자 → 재고 1 확인

A 사용자 → 구매
B 사용자 → 구매
```

두 요청이 동시에 성공하면 재고 정합성 문제 발생.

영화 좌석도 동일한 좌석에 동시에 예매 요청이 들어오면 중복 예매 가능.

비관적 락을 사용하면 데이터를 조회할 때부터 잠금을 걸어 다른 트랜잭션의 수정을 제한할 수 있습니다.

사용하기 좋은 경우

- 영화 좌석 중복 예매 방지
- 매점 재고 차감
- 동시에 수정되면 문제가 발생하는 데이터

---

## 2. 반복되는 메서드는 Service의 private 메서드로 분리

반복되는 조회 코드

```text
userRepository.findById(userId)
        .orElseThrow(...);
```

다음과 같이 분리

```text
private User getUser(Long userId) {
    return userRepository.findById(userId)
            .orElseThrow(...);
}
```

장점

- 중복 코드 감소
- 예외 처리 방식 통일
- 서비스 메서드에서 핵심 로직만 확인 가능

---

## 3. 도메인별 Exception과 ErrorCode 분리

초기에는 공통 ErrorCode 하나로 관리하려 했지만 도메인이 늘어나면서 관리하기 어려워졌습니다.

```
movie
 ├─ MovieException
 └─ MovieErrorCode

reservation
 ├─ ReservationException
 └─ ReservationErrorCode

theater
 ├─ TheaterException
 └─ TheaterErrorCode
```

도메인별로 예외를 분리하니 관련 에러를 찾고 수정하기 편해졌습니다.

---

# 서비스 단위 테스트를 하며 느낀 점

서비스 단위 테스트를 작성하면서 정상 동작뿐 아니라 예외 상황을 더 많이 생각하게 됐습니다.

테스트한 예시

```
- 존재하지 않는 사용자
- 존재하지 않는 상영 일정
- 이미 예매된 좌석
- 매점 재고 부족
- 이미 찜한 영화관
```

Repository를 Mocking해 Service만 테스트하면서 DB나 Controller와 분리된 상태로 비즈니스 로직을 검증할 수 있었습니다.

특히 테스트를 작성하면서

```
이 메서드는 어떤 경우에 실패해야 하는가?
```

를 계속 생각하게 되어 예외 케이스를 놓치는 일이 줄었습니다.

테스트 코드는 단순 확인용 코드라기보다 서비스의 규칙을 다시 정리하는 과정이라고 느꼈습니다.

---

# 세션 관련 정리 

## 1. ORM과 JPA

### Q. `flush`는 언제 발생할까?

대표적인 시점

1. `em.flush()` 직접 호출
2. 트랜잭션 Commit 직전
3. JPQL 실행 직전

`flush`는 영속성 컨텍스트의 변경 내용을 DB에 SQL로 반영하는 과정입니다.

### `em.flush()` 직접 호출

```text
em.persist(user);
em.flush();
```

직접 호출하는 즉시 변경 내용 DB에 반영.

### 트랜잭션 Commit

```java
@Transactional
public void updateUser() {
    User user = userRepository.findById(1L).get();
    user.changeName("영희");
}
```

흐름

```
Entity 변경
   ↓
변경 감지
   ↓
flush
   ↓
UPDATE SQL
   ↓
commit
```

### JPQL 실행 직전

```text
em.persist(member);

em.createQuery("select m from Member m", Member.class)
        .getResultList();
```

JPQL은 DB를 직접 조회하기 때문에 영속성 컨텍스트와 DB 상태가 다르면 조회 결과가 달라질 수 있습니다.

기본 `FlushModeType.AUTO`에서는 필요한 경우 JPQL 실행 전에 flush를 수행합니다.

정리

```
flush = 영속성 컨텍스트와 DB 상태 동기화
commit = 트랜잭션 확정
```

따라서

```
flush ≠ commit
```

---

### Q. 영속성 컨텍스트와 EntityManager는 항상 1:1일까?

항상 1:1은 아닙니다.

```
EntityManager
      ↓
Persistence Context
      ↓
Managed Entity
```

EntityManager는 영속성 컨텍스트에 접근하고 엔티티를 관리하기 위한 인터페이스 역할.

Spring에서는 트랜잭션 범위에 맞춰 EntityManager를 관리하기 때문에 직접 생성 / 종료하는 경우가 많지 않습니다.

핵심

```
EntityManager를 통해 영속성 컨텍스트에 접근
```

---

### Q. EntityManager와 Transaction은 항상 1:1일까?

항상 1:1은 아닙니다.

하나의 EntityManager에서 여러 트랜잭션을 순차적으로 실행할 수 있습니다.

```text
EntityManager em = emf.createEntityManager();

EntityTransaction tx1 = em.getTransaction();
tx1.begin();
// 작업
tx1.commit();

EntityTransaction tx2 = em.getTransaction();
tx2.begin();
// 다른 작업
tx2.commit();

em.close();
```

역할 차이

```
EntityManager → 엔티티 관리

Transaction → 작업 단위 관리
```

Spring에서는 대부분 `@Transactional`을 통해 트랜잭션을 관리합니다.

---

# 2. 로딩 전략과 N+1 문제

### Q. 양방향 매핑은 항상 좋은 것인가?

장점

- 양쪽 방향 탐색 가능
- 조회 편의성

단점

- 연관관계 관리 복잡
- 연관관계의 주인 관리 필요
- JSON 직렬화 시 순환 참조 가능
- 엔티티 간 의존성 증가

예시

```
User
 → Reservation
   → User
     → Reservation
       → ...
```

결론

```
기본은 단방향
반대 방향 탐색이 실제로 필요할 때만 양방향 적용
```

---

### Q. Proxy란?

Proxy는 실제 엔티티를 바로 조회하지 않고 먼저 만들어지는 대리 객체입니다.

```java
Member member = em.getReference(Member.class, 1L);
```

흐름

```
Proxy 생성
   ↓
member.getName()
   ↓
실제 데이터 필요
   ↓
SELECT 실행
```

Lazy Loading을 가능하게 하는 방식 중 하나.

---

### Q. Proxy와 N+1 문제의 관계

Lazy Loading에서는 연관 엔티티가 Proxy 형태로 존재할 수 있습니다.

예시

```text
List<Movie> movies = movieRepository.findAll();

for (Movie movie : movies) {
    movie.getSomething().getName();
}
```

쿼리 흐름

```
Movie 전체 조회 → 1번

Movie1 연관 데이터 → 1번
Movie2 연관 데이터 → 1번
Movie3 연관 데이터 → 1번
...
```

결과

```
1 + N번 Query
```

Proxy 자체가 문제라기보다는 반복문 안에서 Lazy Proxy가 계속 초기화되면서 추가 쿼리가 발생하는 것이 문제.

해결 방법 예시

- Fetch Join
- EntityGraph

---

### Q. Hibernate Proxy와 Spring AOP Proxy의 차이

| 구분 | Hibernate Proxy | Spring AOP Proxy |
| --- | --- | --- |
| 목적 | 지연 로딩 | 부가 기능 적용 |
| 대상 | JPA Entity | Spring Bean |
| 대표 기능 | Lazy Loading | Transaction, Logging |
| 동작 | 데이터 필요 시 조회 | 메서드 호출 전후 로직 수행 |

간단히 정리하면

```
Hibernate Proxy
→ 언제 엔티티를 조회할 것인가?

Spring AOP Proxy
→ 메서드 실행 전후에 무엇을 할 것인가?
```

예를 들어 `@Transactional`은 Spring AOP Proxy가 메서드 호출을 가로채 트랜잭션을 시작하고 종료합니다.

---

### Q. 양방향 매핑 + `@OneToOne` + `nullable = true`에서 Proxy 문제가 발생하는 이유는?

예를 들어 다음과 같은 관계가 있다고 가정합니다.

```
User ↔ UserProfile
```

`UserProfile`이 존재하지 않을 수도 있다면 Hibernate는 다음 중 어떤 값인지 판단해야 합니다.

```
UserProfile Proxy
```

또는

```
null
```

연관 엔티티 존재 여부를 FK만으로 확인하기 어려운 방향이라면 실제 DB 조회가 필요할 수 있습니다.

그래서 `@OneToOne(fetch = LAZY)`를 사용하더라도 상황에 따라 추가 SELECT가 발생할 수 있습니다.

정리

```
@OneToOne + nullable 관계
→ 연관 객체 존재 여부 확인 필요
→ Proxy만으로 판단하기 어려운 경우 DB 조회
```

따라서 `LAZY`를 설정했다고 무조건 지연 로딩된다고 생각하기보다 실제 발생하는 SQL을 확인하는 것이 중요합니다.

---

# 마무리

이번 미션에서 가장 많이 배운 부분은 단순 API 구현보다 데이터 관계를 어떻게 설계할지 고민하는 과정이었습니다.

특히 `SeatTemplate`처럼 구현하면서 처음 ERD에 없던 구조가 필요하다는 것을 알게 되었고, 모델링도 실제 비즈니스 로직을 작성하면서 계속 수정될 수 있다는 점을 경험했습니다.

또한

- JPA 연관관계
- Lazy Loading과 N+1
- 좌석 / 재고 동시성 처리
- 도메인별 예외 처리
- 서비스 단위 테스트

등을 직접 적용해보면서 기능 구현 외에 데이터 정합성과 코드 구조까지 같이 고민할 수 있었습니다.