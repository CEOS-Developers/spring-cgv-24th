# 2주차

<details>
<summary><strong>ERD 및 DB 모델링</strong></summary>

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

[https://www.erdcloud.com/d/2LXYJEwkeevufXZwr](https://www.erdcloud.com/d/2LXYJEwkeevufXZwr)

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

</details>

---

<details>
<summary><strong>💡 미션 후 새롭게 알게 된 점</strong></summary>

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

</details>

---

<details>
<summary><strong>세션 관련 정리</strong></summary>

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

</details>

---

<details>
<summary><strong>마무리</strong></summary>

이번 미션에서 가장 많이 배운 부분은 단순 API 구현보다 데이터 관계를 어떻게 설계할지 고민하는 과정이었습니다.

특히 `SeatTemplate`처럼 구현하면서 처음 ERD에 없던 구조가 필요하다는 것을 알게 되었고, 모델링도 실제 비즈니스 로직을 작성하면서 계속 수정될 수 있다는 점을 경험했습니다.

또한

- JPA 연관관계
- Lazy Loading과 N+1
- 좌석 / 재고 동시성 처리
- 도메인별 예외 처리
- 서비스 단위 테스트

등을 직접 적용해보면서 기능 구현 외에 데이터 정합성과 코드 구조까지 같이 고민할 수 있었습니다.

</details>

# 3주차

<details>
<summary><strong>JWT</strong></summary>

### JWT 를 이용한 인증 흐름

#### JWT란 ?

→ JSON WEB TOKEN 의 약자 , 속성 정보를 JSON 데이터 구조로 표현한 토큰 ( 다른 장치끼리 안전하게 전송하기 위해 설계됨 )

#### JWT 의 Header , Payload , Signature 의 역할

→ JWT는 3개의 파트로 이루어지며 , 각 파트는 점으로 구분됨.
```sql
Header.Payload.Signature 

```
![jwt 구조](docs/images/jwt.png)

- header : 토큰 종류와 서명 알고리즘 명시

ex )
```sql
{
  "alg": "HS256", ( 서명 생성에 사용된 해시 알고리즘 ) 
  "typ": "JWT" ( 토큰의 유형 ) 
}

```

- payload : 사용자 식별자, 만료시간 등의 정보인 claim (=정보의 한조각 )저장&#x20;
   - name/value 한 쌍으로 이루어짐
```sql
{
  "sub": "123",( 토큰의 주체, 보통 사용자의 ID )
  "role": "USER", ( 서비스에서 정의한 사용자 권한 ) 
  "exp": 1800000000 ( 토큰 만료 시간 ) 
}

```

⇒ Header과 Payload는 Base64URL 인코딩이므로 누구나 읽을 수 있음 ( 그러므로 비밀번호 같은 민감 정보는 넣으면 안됨 ! )

- signature : Header 와 Payload 가 변조되지 않았는지, 신뢰하는 발급자가 서명했는지 검증함

→ Header , Payload , Secret Key 를 합쳐서 암호화한 결과값

⇒ 비밀 키 또는 개인 키로 생성한 서명
```sql
인코딩된 Header + "." + 인코딩된 Payload
                     ↓
           비밀 키로 서명 계산
                     ↓
                 Signature

```

→ 비밀 키 = 서버만 알고 있고 , JWT 안에는 들어있지 않음.

---

- **Payload가 변경되는 상황을 생각해보자 .**
```text
원래 Payload: 사용자 123, 권한 USER
변조 Payload: 사용자 123, 권한 ADMIN

```

→ payload 자체를 바꾸는 건 가능하지만, 내용이 바뀌면 그 내용에 맞는 서명도 달라져야 함 !

---

### JWT의 흐름

1 . 사용자가 로그인을 시도함

⇒ 서버는 DB에서 사용자를 조회하고, 저장된 비밀번호 해시와 비교해서 로그인 정보가 맞는지 확인

2 . 서버가 JWT를 만들어서 응답

⇒

로그인에 성공하면 서버는 사용자 ID, 만료 시간 등을 Payload에 담고, 서명을 만들어 JWT를 발급
```
Header   : 서명 알고리즘
Payload  : 사용자 ID = 123, 만료 시각 등
Signature: Header와 Payload를 키로 서명한 값

```

클라이언트는 받은 JWT를 보관 !! 이제 요청할 때마다 아이디와 비밀번호를 다시 보낼 필요 X

3 . 클라이언트가 API 요청에 JWT 를 함께 보냄

→ ex ) 사용자가 내 정보를 요청하면 다음처럼 요청함 .

```text
GET /users/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

→ Bearer : 이 토큰을 인증 수단으로 제시함 .

4 . 서버가 JWT를 검증함 .

→ 허용한 서명 알고리즘인지 , 서명이 유요한지 , 만료 시간이 지나지 않았는지 등등을 확인

5 . 검증에 성공하면 사용자를 식별하고 요청을 처리함

---

### Access Token과 Refresh Token의 차이점

#### Access Token

→ API 요청을 보낼 때 **사용자를 인증하는 토큰**

- 만료 시간이 비교적 짧음
- 서버는 토큰을 검증해 사용자를 식별하고 요청을 처리
- 토큰이 탈취되더라도 사용할 수 있는 시간을 줄이기 위해 짧게 설정함

#### Refresh Token

→ 만료된 Access Token을 다시 발급받을 때 사용하는 토큰

- Access Token보다 만료 시간이 김
- 일반 API 요청에는 보내지 않고, 토큰 재발급 요청에만 사용
- 서버는 Refresh Token이 유효한지 확인한 뒤 새로운 Access Token을 발급
```
1. 로그인 성공
   → Access Token + Refresh Token 발급

2. API 요청
   → Access Token을 함께 보냄

3. Access Token 만료
   → Refresh Token으로 재발급 요청

4. Refresh Token 검증 성공
   → 새로운 Access Token 발급

```

⇒ Access Token은 **API 이용용**, Refresh Token은 **Access Token 재발급용**이라고 이해하면 됨.

---

### 쿠키와 세션, JWT의 역할

각각 담당하는 역할이 다름.

| 구분  | 역할                        | 예시                          |
| --- | ------------------------- | --------------------------- |
| 쿠키  | 브라우저에 데이터를 저장하고 요청에 함께 보냄 | 브라우저가 토큰을 쿠키에 저장            |
| 세션  | 서버가 로그인 상태를 저장하고 관리함      | 세션 ID로 서버의 로그인 정보 조회        |
| JWT | 사용자 정보와 서명을 담은 토큰 형식      | `Authorization: Bearer ...` |

#### 세션 방식
```
로그인 성공
→ 서버가 로그인 정보를 세션 저장소에 저장
→ 세션 ID를 쿠키로 전달
→ 클라이언트가 이후 요청마다 세션 ID 전송
→ 서버가 세션 ID로 로그인 정보를 조회

```

⇒ 로그인 상태를 서버가 관리함.

#### JWT 방식
```
로그인 성공
→ 서버가 JWT 발급
→ 클라이언트가 JWT 저장
→ 이후 요청마다 JWT 전송
→ 서버가 서명과 만료 시간을 검증

```

⇒ JWT 안에 사용자를 식별할 정보가 들어 있고, 서버는 토큰의 유효성을 확인함.

#### 쿠키와 JWT는 함께 사용할 수 있음

JWT를 어디에 저장하고 어떻게 전달할지는 별도의 선택임.
```
Authorization 헤더에 담아 전송
또는
쿠키에 저장해 브라우저가 자동으로 전송

```

⇒ **쿠키는 저장·전달 방식**, **JWT는 토큰의 형식**임.

---

### CGV 클론코딩에서의 Access Token: 어떤 claim이 필요한가?

Access Token에는 요청을 처리하는 데 필요한 최소한의 정보를 담는 것이 좋음.
```
{
  "sub": "123",
  "role": "USER",
  "iat": 1800000000,
  "exp": 1800003600
}

```

- `sub`: 토큰의 주체. 보통 회원 ID
- `role`: 서비스에서 정의한 권한
- `iat`: 토큰이 발급된 시각
- `exp`: 토큰 만료 시각

CGV 클론코딩에서 회원 ID가 `123`인 사용자가 영화 예매 요청을 보내면, 서버는 `sub`를 이용해 사용자를 식별할 수 있음.
```
POST /reservations
Authorization: Bearer <Access Token>

```

⇒ 서버는 토큰의 `sub`를 확인해 어떤 회원의 예매 요청인지 판단함.

주의할 점은 JWT의 Payload가 암호화된 것이 아니라는 점!!

따라서 비밀번호, 카드 정보 등 민감한 정보는 넣지 않으면 X .

또한 역할이 바뀌면 기존 토큰의 권한 정보가 만료 전까지 남을 수 있으므로, 토큰 만료 시간을 적절히 설정해야 !!

---

### JWT 검증 결과와 Authentication, SecurityContext의 연결성

JWT 검증이 끝나면 서버는 토큰의 사용자 정보를 Spring Security가 사용할 수 있는 `Authentication` 객체로 변환함.
```
요청에 JWT 포함
→ JWT 서명·만료 시간 검증
→ 토큰에서 회원 ID와 권한 추출
→ Authentication 객체 생성
→ SecurityContext에 저장
→ 컨트롤러와 서비스에서 인증 정보 사용

```

`Authentication`에는 보통 다음 정보가 들어감.

- `Principal`: 인증된 사용자 정보
- `Authorities`: 사용자의 권한
- `Credentials`: 인증에 사용된 정보. JWT 인증에서는 비워 두거나 사용하지 않는 경우가 많음
```
Principal: 회원 ID 123
Authorities: ROLE_USER

```

`SecurityContext`는 현재 요청에서 인증된 사용자의 `Authentication`을 보관함. 그래서 컨트롤러나 서비스에서는 매번 JWT를 직접 해석하지 않고 인증 정보를 사용할 수 있음.

⇒ 쉽게 말하면,

**JWT는 요청으로 전달되는 인증 증표**,

**Authentication은 검증 후 Spring Security가 사용하는 인증 정보**,

**SecurityContext는 그 인증 정보를 담아 두는 공간.**

---

### 인증과 인가의 차이

#### 인증(Authentication)

→ **누구인지 확인하는 과정**
```
이 요청을 보낸 사용자는 회원 ID 123인가?

```

예: 로그인 정보 확인, JWT 검증

#### 인가(Authorization)

→ **해당 작업을 할 권한이 있는지 확인하는 과정**
```
회원 ID 123이 관리자 전용 영화 등록 기능을 사용할 수 있는가?

```

예: 일반 회원은 예매 가능, 관리자만 영화 등록 가능

⇒ 인증은 **신원 확인**, 인가는 **권한 확인**임. 보통 인증을 먼저 하고, 그다음 인가를 확인함.

---

### 401과 403의 반환 시점

#### 401 Unauthorized

→ 인증에 실패했을 때 반환

- JWT가 없음
- JWT 형식이나 서명이 잘못됨
- JWT가 만료됨
- 로그인 정보가 확인되지 않음
```
인증되지 않은 사용자가 내 예매 목록 요청
→ 401 Unauthorized

```

#### 403 Forbidden

→ 인증은 되었지만, 요청을 수행할 권한이 없을 때 반환
```
일반 회원이 관리자 전용 영화 등록 요청
→ 403 Forbidden

```

⇒ **401은 “누구인지 확인할 수 없음”**, \*\*403은 “누구인지는 알지만 권한이 없음”\*\*으로 구분하면 됨.

---

### OAuth 2.0과 JWT의 차이

OAuth 2.0과 JWT는 같은 종류의 개념이 아님.

#### OAuth 2.0

→ 사용자가 다른 서비스의 권한을 안전하게 위임하는 **인가 프레임워크**

예: 카카오 로그인을 통해 사용자 정보를 받아 우리 서비스에 가입하거나 로그인

#### JWT

→ 정보를 JSON 형태로 담아 전달하는 **토큰 형식**
```
OAuth 2.0: 토큰을 발급하고 사용하는 절차와 규칙
JWT: 토큰을 표현하는 형식 중 하나

```

OAuth 2.0에서 발급하는 토큰은 JWT일 수도 있고, 내부 정보를 읽을 수 없는 불투명 토큰일 수도 있음.

또한 로그인한 사용자의 신원 정보를 표준 방식으로 확인하는 데에는 OAuth 2.0을 기반으로 한 \*\*OpenID Connect(OIDC)\*\*가 사용됨.

⇒ 정리하면, **OAuth 2.0은 권한 위임을 위한 규칙**, **JWT는 토큰을 표현하는 방식**임.

</details>

<details>
<summary><strong>정상/실패 상황 테스트</strong></summary>

### 정상·실패 상황 테스트

- 회원가입

![회원가입 테스트](docs/images/test1.png)


→ 회원가입 완료 : 올바른 회원 정보를 입력한 사용자가 회원가입에 성공하는 것을 확인함.

- 올바른 로그인 정보 ( Access Token 발급 )

![로그인 테스트](docs/images/login.png)

회원가입을 완료한 사용자 :  올바른 아이디와 비밀번호를 입력하면 인증에 성공하고, Access Token이 발급되는 것을 확인함.

- 없는 계정/ 잘못된 비밀번호로 로그인

![로그인 에러 테스트](docs/images/loginerror.png)


![로그인 에러 테스트](docs/images/loginerror.png)

→ **없는 계정으로 로그인**: 가입되지 않은 아이디로 로그인을 시도하면 `LOGIN_FAILED`와 401 상태 코드를 반환하고, Access Token을 발급하지 않음.

→ **로그인 실패 응답 통일**: 없는 계정과 잘못된 비밀번호에 동일한 응답을 반환하도록 구성함. 이를 통해 아이디의 가입 여부가 외부에 노출되지 않도록 처리함.

- 토큰 없이 공개 API 호출


![공개 api 관련](docs/images/notoken.png)

Access Token을 포함하지 않고 영화 조회 API를 호출해도 영화 정보가 정상적으로 반환되는 것을 확인.

→ 이를 통해 영화 조회는 로그인하지 않은 사용자도 이용할 수 있도록 공개 API로 설정했음을 확인함.

- 정상 토큰으로 보호된 API 호출 ( 영화 찜 처리 )


![영화 찜 처리](docs/images/movieliketoken.png)


→ 유효한 Access Token을 포함해 영화 찜 취소 API를 호출했을 때 정상 처리되는 것을 확인 !

토큰에서 인증된 사용자 정보를 `@AuthenticationPrincipal`로 전달받아 해당 사용자의 영화 찜을 취소하고, 성공 응답을 반환함.

- 토큰 없이 보호된 API 호출 (401 + 공통 JSON )


![영화 찜 처리 관련](docs/images/movielikenotoken.png)


→ 로그인하지 않은 상태:  영화 찜 API를 호출하면 인증 토큰이 없어 요청이 거부,

401 상태 코드와 `TOKEN_NOT_EXIST` 응답을 반환하는 것을 확인.

따라서 로그인한 사용자만 영화 찜 기능을 이용할 수 있음.

- 만료된 토큰

![토큰 관련](docs/images/tokenexpire.png)

→ 짧은 토큰 만료 시간을 적용해서 AccessToken 만료 이후, 보호 API 호출 :

401 상태코드와 `TOKEN_EXPIRED` 응답을 반환하는 것을 확인.

- 변조된 토큰

![토큰 관련](docs/images/movielikedtoken.png)


→ 정상 Access Token의 서명 부분을 임의로 변경한 뒤 영화 찜 API를 호출함.

서버가 토큰의 서명을 검증하는 과정에서 변조를 감지해 요청을 거부하고, 401 상태 코드와 `TOKEN_INVALID` 오류 코드를 반환하는 것을 확인함.

- 다른 키로 서명한 토큰

![토큰 관련](docs/images/movielikedtoken.png)


→ 서버가 사용하는 키와 다른 키로 서명한 토큰으로 보호된 API를 호출.

토큰의 서명 검증에 실패해 요청이 거부되고, 401 상태 코드와 유효하지 않은 토큰 오류 코드가 반환되는 것을 확인함.

- 일반 사용자로 관리자 API 호출

![admin 관련](docs/images/useradmindeny.png)


→ 유효한 Access Token을 가진 일반 사용자가 관리자 전용 API를 호출.

인증에는 성공했지만 관리자 권한이 없어 403 상태 코드와 공통 JSON 응답이 반환되는 것을 확인함.

- 관리자로 관리자 API 호출


![admin 관련](docs/images/adminok.png)


→ 관리자 권한이 포함된 유효한 Access Token으로 관리자 전용 API를 호출.

인증과 권한 검증에 모두 성공해 요청이 정상 처리되는 것을 확인함.

- 정상 인증 요청 직후 , 토큰 없이 보호된 API 호출


![admin 관련](docs/images/stateless.png)


→ Access Token을 포함한 요청이 정상 처리된 직후, 토큰 없이 보호된 API를 호출.

이전 요청의 인증 정보가 다음 요청에 유지되지 않아 401 상태 코드가 반환되는 것을 확인함.

</details>
