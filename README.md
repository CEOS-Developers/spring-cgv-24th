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

# 4주차

<details>
<summary><strong>영화 예매 결제와 동시성 제어</strong></summary>

이번 미션에서는 CGV 영화 예매에 토스페이먼츠 테스트 결제를 연동했다. 영화 예매를 선택한 이유는 **좌석을 언제 확보하고, 언제 확정하며, 취소하면 어느 좌석을 돌려줄지**를 결제 흐름과 함께 설계할 수 있기 때문이다.

두 가지 기준을 세웠다. 첫째, 같은 상영 회차의 같은 좌석은 한 예매만 확보해야 한다. 둘째, 한 좌석을 취소하면 그 좌석의 예매 당시 가격만큼만 환불하고, 같은 요청을 다시 보내도 환불 금액이 늘어나지 않아야 한다.

</details>

<details>
<summary><strong>1. 코드 리팩토링 — 데이터와 책임 분리</strong></summary>

| 변경한 부분 | 변경 내용과 이유 |
| --- | --- |
| 좌석 점유 관리 | `Seat`는 상영관의 물리 좌석을, `ScreeningSeat`는 특정 회차에서 그 좌석의 점유 상태를 관리한다. 같은 상영관의 A1 좌석도 오후 2시와 오후 5시에는 각각 예매할 수 있기 때문이다. |
| 예매 금액 계산 | `ReservationSeat`에 예매 당시 좌석 가격을 저장하고, `Reservation`은 좌석을 추가하면서 그 가격을 합산한다. 이후 가격이 바뀌어도 부분 취소 금액은 실제 주문 금액을 기준으로 계산할 수 있다. |
| 취소 이력 관리 | `PaymentCancellation`에 취소 건을 기록하고, `PaymentCancellationSeat`로 해당 취소 건과 예매 좌석을 연결한다. 예매 좌석 이력을 보존하면서 어떤 요청으로 어느 좌석이 취소됐는지 추적할 수 있다. |
| 결제 처리 분리 | `PaymentTransactionService`는 DB 검증과 상태 변경을, `TossPaymentClient`는 외부 HTTP 통신을 맡는다. `PaymentCancellationService`가 준비 → 외부 호출 → 결과 저장 순서를 조율하고, `PaymentQueryService`는 소유자를 확인한 뒤 저장된 결제를 조회한다. |

특히 취소 처리에서 이 분리가 중요했다. Toss 응답을 기다리는 동안 DB 락을 유지하면 같은 주문에 대한 다른 요청도 네트워크 응답이 올 때까지 기다린다. 그래서 **요청 준비와 결과 저장을 각각 짧은 트랜잭션으로 처리하고, 그 사이에서 Toss를 호출**하도록 구성했다.

</details>

<details>
<summary><strong>2. 동시성 문제 발견과 해결 방법 선택</strong></summary>

동시성 문제는 여러 요청이 같은 데이터를 읽고 변경할 때, **각 요청이 내린 판단이 저장 시점에는 이미 유효하지 않아서** 발생할 수 있다. 이 서비스에서는 두 지점이 해당했다.

- **좌석 예매** — 두 요청이 모두 `reserved = false`를 읽으면 각각 예매를 생성할 수 있다. 최종 좌석 값은 어느 경우든 `true`이므로, 중복 예매 여부를 확인하려면 예매 이력도 봐야 한다.
- **결제 승인** — 같은 주문의 두 요청이 모두 `READY`를 읽으면 각각 Toss 승인을 시작할 수 있다. 승인 가능 여부를 확인하고 `APPROVING`으로 변경하는 과정이 한 번에 처리돼야 한다.

| 방법 | 동작 원리와 이번 서비스에서의 판단 |
| --- | --- |
| `synchronized` | 같은 JVM에서 같은 잠금 객체를 사용하는 요청을 순서대로 처리할 수 있다. 서버가 여러 대로 늘어나면 각 서버가 서로 다른 잠금 객체를 사용한다. |
| 낙관적 락 | 버전 값으로 다른 트랜잭션이 먼저 수정했는지 확인한다. 충돌이 드물 때 유리하지만, 인기 좌석처럼 요청이 몰리면 저장 시점에 충돌한 요청을 처리해야 한다. |
| 조건부 `UPDATE` | `WHERE reserved = false`인 행만 갱신하고 변경된 행 수로 성공 여부를 판단한다. 단일 좌석에는 간결하다. 여러 좌석을 한 번에 예매할 때는 요청 수와 갱신 수를 비교하고 일부만 성공하면 전체를 롤백해야 한다. |
| **비관적 락** | 대상 행을 먼저 잠근 뒤 최신 상태를 검사한다. 같은 행을 기다리는 비용이 있지만, 여러 좌석을 조회하고 모두 검증한 뒤 함께 점유하는 기존 JPA 흐름에 적용하기 쉽다. |

**좌석에는 회차별 비관적 락을 선택했다.** `(screening_id, seat_id)` 유니크 제약은 회차별 점유 정보를 한 행으로 유지한다. `findAllForUpdate()`는 그 행을 잠가 동시에 들어온 요청들이 순서대로 점유 여부를 판단하게 한다. 유니크 제약과 락이 각각 다른 역할을 맡는다.

결제 승인도 같은 원리를 적용했다. 예매 행 → 결제 행 순서로 잠그고 `READY → APPROVING`을 커밋한 뒤 외부 승인을 시작한다. 뒤따르는 요청은 변경된 상태를 확인하므로 같은 주문의 승인을 다시 시작하지 않는다.

</details>

<details>
<summary><strong>3. 주문 → 결제 승인 → 조회 → 취소</strong></summary>

### 주문과 상태 전이

사용자가 상영 회차와 좌석을 선택하면 서버가 좌석 가격을 합산하고 UUID 형식의 `orderId`를 생성한다. 테스트 환경의 시크릿 키는 `TOSS_SECRET_KEY` 환경변수에서 읽는다.

| 시점 | 예매 상태 | 결제 상태 | 좌석 처리 |
| --- | --- | --- | --- |
| 예매 생성 | `PENDING_PAYMENT` | `READY` | 선택한 좌석 확보 |
| 승인 준비 완료 | `PENDING_PAYMENT` | `APPROVING` | 확보 상태 유지 |
| 승인 결과 저장 | `CONFIRMED` | `DONE` | 예매 확정 |
| 일부 좌석 취소 완료 | `PARTIALLY_CANCELED` | `PARTIALLY_CANCELED` | 취소한 좌석만 반환 |
| 전체 취소 완료 | `CANCELED` | `CANCELED` | 취소 대상 좌석 전체 반환 |

### 승인 전에 무엇을 검증했는가?

결제창 인증 후 전달받는 `paymentKey`, `orderId`, `amount`만으로 결제를 확정하지 않는다. `orderId`로 주문을 찾고 다음 항목을 서버 데이터와 비교한다.

1. 로그인한 사용자가 예매 소유자인지 확인한다.
2. 요청의 `amount`가 서버에서 계산해 저장한 최초 주문 금액과 같은지 확인한다.
3. 결제가 `READY`이고 예매가 `PENDING_PAYMENT`인지, 상영 시작 시간이 지나지 않았는지 확인한다.
4. 이미 승인된 동일 요청이면 저장된 결제 결과를 반환한다. 새 승인이라면 `APPROVING`을 저장한 뒤 Toss를 호출한다.
5. 승인 응답의 주문번호·결제 키·금액·상태를 검증한 뒤 결제 `DONE`과 예매 `CONFIRMED`를 한 트랜잭션에서 반영한다.

**결제 조회는 서비스 DB를 사용한다.** 사용자가 보는 정보는 자신의 예매 상태, 최초 결제 금액, 누적 취소 금액, 승인 시각이다. 이 정보는 서비스가 직접 관리한다. 반면 외부 요청의 성공 여부가 불명확할 때는 Toss의 거래 결과와 대조할 필요가 있다.

### 전체·부분 취소

전체 취소 시 서버는 주문의 `paymentKey`를 찾아 취소 사유를 전달한다. 부분 취소 시에는 사용자가 선택한 `reservationSeatId`로 예매 좌석을 확인하고, **그 좌석들의 예매 당시 가격 합계**를 `cancelAmount`로 보낸다. Toss는 같은 취소 API에서 `cancelAmount`를 생략하면 전액 취소하고, 지정하면 해당 금액을 취소한다. [토스페이먼츠 개발자센터](https://docs.tosspayments.com/guides/v2/cancel-payment)

취소 가능 잔액은 `최초 결제 금액(amount) - 누적 취소 금액(canceledAmount)`으로 계산한다. 취소 결과를 반영할 때 최초 금액은 보존하고, 취소 이력과 대상 좌석을 기록한다.

| 부분 취소 검증 항목 | 실제 확인 결과 |
| --- | --- |
| 테스트 조건 | 예매 4번, 15,000원 좌석 2개, 총 30,000원 |
| 1개 좌석 취소 후 | 결제·예매 모두 `PARTIALLY_CANCELED` |
| 금액 | 최초 금액 30,000원, 누적 취소 15,000원, 잔액 15,000원 |
| 좌석 | 취소한 좌석 3은 `reserved = 0`, 유지한 좌석 2는 `reserved = 1` |
| 동일 취소 요청 재전송 | 누적 취소 금액이 15,000원으로 유지됨 |

</details>

<details>
<summary><strong>4. 동시성 제어 적용 전후 테스트</strong></summary>

상영 회차 1에서 빈 좌석 하나를 고르고 `POST /api/reservations`에 같은 요청 두 건을 보냈다. 브라우저 콘솔에서 `Promise.all([reserve(1), reserve(2)])`을 사용해 첫 번째 응답을 기다리지 않고 두 번째 요청을 시작했다. 적용 전에는 좌석 7, 적용 후에는 좌석 3을 사용했다. 각 실행에서 두 요청이 경쟁하는 대상은 동일했다.

| 조건 | API 응답 | 확인 결과 |
| --- | --- | --- |
| 락 적용 전 · 좌석 7 | `200 COMMON2000` 2건 | 같은 회차·좌석에 보낸 두 요청이 모두 성공 응답을 받음 |
| 락 적용 후 · 좌석 3 | `200 COMMON2000` 1건, `409 RESERVATION4091` 1건 | 22:48:19에 예매 5번(`PENDING_PAYMENT`) 한 건만 새로 생성됨 |

적용 후 결과는 아래 SQL로 확인했다. 좌석 3에는 이전에 취소한 예매 이력이 있었으므로 **테이블 전체 행 수 대신 테스트 시각에 생성된 행**을 확인했다.

```sql
SELECT r.reservation_id, r.status, r.created_at
FROM reservations r
JOIN reservation_seats rs ON rs.reservation_id = r.reservation_id
WHERE r.screening_id = 1 AND rs.seat_id = 3
ORDER BY r.created_at DESC;
```

</details>

<details>
<summary><strong>5. 중복 결제와 멱등성</strong></summary>

**멱등성은 같은 작업을 다시 요청해도 결제 금액이나 환불 금액이 한 번 처리했을 때와 같게 유지되는 성질이다.** 예를 들어 사용자가 15,000원짜리 좌석 취소를 눌렀는데 응답을 받지 못할 수 있다. 이때 같은 요청을 다시 보냈다는 이유로 나머지 좌석까지 환불되면 안 된다.

| 작업 | 같은 요청을 식별하고 처리하는 방법 | 확인한 결과 |
| --- | --- | --- |
| 결제 승인 | 같은 주문의 승인 요청에는 `confirm-{orderId}`를 Toss 멱등키로 사용한다. 이미 `DONE`이고 결제 키와 금액이 일치하면 저장된 결과를 사용한다. | Swagger에서 동일 승인 요청을 재전송했고 두 응답의 `approvedAt`이 같았다. |
| 결제 취소 | 취소 건에 멱등키를 저장하고 Toss 호출에 전달한다. 부분 취소는 `requestId`로 같은 취소 건을 구분한다. 완료된 요청을 다시 받으면 현재 결제 정보를 반환한다. | 동일 부분 취소 요청을 재전송해도 `canceledAmount`가 증가하지 않았다. |

부분 취소에서는 **주문 하나에 정상적인 취소가 여러 번** 발생할 수 있다. 좌석 A를 취소한 뒤 좌석 B를 취소하면 서로 다른 작업이고, 좌석 A의 취소 응답을 받지 못해 다시 보내면 같은 작업이다. 그래서 취소 멱등키는 주문 전체가 아니라 **취소 요청 한 건**에 연결했다. Toss의 `Idempotency-Key`는 최초 요청일부터 15일간 유효하므로, 서비스에서도 주문 상태와 취소 이력을 보관한다. [토스페이먼츠 개발자센터](https://docs.tosspayments.com/reference/using-api/authorization)

이번 프로젝트에서 **동시성 제어**가 답하는 질문은 “두 요청이 지금 같은 좌석을 확보하려고 할 때 누가 먼저 상태를 바꿀 수 있는가?”이다. **멱등성**이 답하는 질문은 “방금 받은 요청이 이미 처리한 환불의 재전송인가?”이다. 같은 취소 요청은 첫 요청이 끝난 뒤에도 다시 올 수 있으므로, 락을 해제한 이후에는 저장해 둔 요청 식별자와 처리 결과가 필요하다.

</details>

<details>
<summary><strong>6. HTTP 클라이언트 선택과 외부 API 오류 처리</strong></summary>

| 방식 | 장점 | 이번 프로젝트에서 고려한 비용 |
| --- | --- | --- |
| Feign Client | 인터페이스에 외부 API 계약을 선언할 수 있어 호출 대상이 많을 때 반복 코드를 줄이기 좋다. | 소수의 Toss API를 위해 Spring Cloud OpenFeign 의존성과 설정을 추가해야 한다. |
| **RestClient** | 동기 호출의 URI, 헤더, 요청 DTO, 응답 DTO를 한 메서드에서 따라가기 쉽다. | 응답을 기다리는 동안 호출 스레드를 사용한다. |
| WebClient | 논블로킹 I/O와 스트리밍을 지원해 여러 외부 응답을 비동기로 조합하기 좋다. | 현재 MVC·JPA 서비스에 적용하려면 비동기 결과 처리 방식까지 고려해야 한다. |

이 서비스의 승인·취소는 외부 응답 한 건을 확인한 뒤 다음 DB 작업을 진행하는 흐름이다. 그래서 **RestClient를 선택하고**, 공통 설정은 `TossConfig`, 승인·취소 요청 구성은 `TossPaymentClient`에 모았다. [Spring Framework 공식 문서](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)

### 현재 처리와 남은 복구 과제

현재 클라이언트는 `retrieve()`에서 HTTP 오류가 발생하거나 응답 본문이 비어 있으면 정상 완료 단계로 진행하지 않는다. 응답을 받은 경우에도 주문과 금액 등을 검증한 뒤 내부 상태를 갱신한다. 다만 **완료 처리를 중단하는 것과, 이미 발생한 외부 거래를 복구하는 것은 별도 문제**다.

| 상황 | 서비스가 취해야 할 처리 |
| --- | --- |
| Toss가 거절 사유를 반환 | 오류 코드로 해당 작업의 실패가 확정됐는지 판단한다. 취소 실패라면 환불액을 더하거나 좌석을 반환하지 않는다. |
| 타임아웃·연결 종료·예상 밖의 응답 | 응답을 받지 못했어도 Toss에서는 처리됐을 수 있다. 실패로 단정하지 않고 주문번호·결제 키·멱등키를 보존해 결과를 다시 확인한다. |
| Toss 성공 후 내부 DB 저장 실패 | Toss 거래를 조회해 주문, 금액, 거래 식별자를 대조한다. 확인된 승인·취소 결과를 DB에 한 번만 반영한다. |

현재 확인한 범위는 **오류 또는 빈 응답에서 정상 완료를 중단하는 경로**까지다. 결과가 불명확한 거래를 자동으로 조회하고 복구하는 과정은 후속 구현·검증 과제다.

</details>

<details>
<summary><strong>7. Webhook — 결제 결과를 놓쳤을 때 어떻게 복구할까?</strong></summary>

### ① Webhook이란?

Webhook은 **이벤트가 발생한 쪽에서 미리 등록된 서버 주소로 변경 사실을 알려주는 방식**이다. 결제 조회 API에서는 우리 서버가 Toss에 “이 주문이 지금 어떤 상태인가요?”라고 요청한다. 웹훅에서는 결제 상태가 바뀌었을 때 Toss가 먼저 우리 서버로 알림을 보낸다. 사용자가 결제 완료 페이지를 닫아도 두 서버 사이에서 결과를 확인할 수 있다는 점이 결제 서비스에서 특히 유용하다. [토스페이먼츠 개발자센터](https://docs.tosspayments.com/guides/v2/webhook)

### ② Toss Payments는 웹훅을 어떻게 제공하는가?

| 단계 | Toss Payments의 동작 |
| --- | --- |
| 등록 | 개발자센터에서 상점 아이디(MID)별로 수신 URL과 구독할 이벤트를 등록한다. 수신 URL은 Toss 서버에서 접근할 수 있어야 한다. |
| 전송 | `PAYMENT_STATUS_CHANGED`와 같이 등록한 이벤트가 발생하면 Toss가 수신 URL로 JSON 본문을 담은 HTTP `POST` 요청을 보낸다. |
| 수신 확인 | 수신 서버는 10초 안에 HTTP `200`으로 응답한다. 전송이 실패하면 Toss가 최대 7회 재전송하므로 같은 이벤트가 여러 번 도착할 수 있다. |

개발자센터에서는 `orderId`나 `paymentKey`로 웹훅 전송 기록을 찾을 수 있다. 따라서 알림이 오지 않았을 때도 **Toss에서 전송에 실패했는지, 우리 서버가 수신한 뒤 처리에 실패했는지** 나누어 조사할 수 있다. [토스페이먼츠 개발자센터](https://docs.tosspayments.com/guides/v2/webhook)

### ③ CGV 결제에서 웹훅이 필요한 순간

현재 승인 흐름은 **DB에 `APPROVING` 저장 → Toss 승인 요청 → DB에 `DONE` 저장 및 예매 확정** 순서다. 여기서 Toss가 승인을 완료한 직후 응답이 유실되거나, 우리 서버가 마지막 DB 저장에 실패할 수 있다. 그러면 사용자는 결제했지만 우리 DB에는 `APPROVING`이 남는다. 일반 결제 조회 API는 우리 DB를 읽으므로, 사용자가 조회 버튼을 여러 번 눌러도 이 차이는 해소되지 않는다.

웹훅을 추가한다면 **PG와 내부 DB의 상태가 어긋난 주문을 찾아 복구하는 역할**을 맡기고 싶다. 이번 과제에서는 다음과 같이 설계했다.

1. **알림을 보관한다.** 수신한 이벤트를 저장한 뒤 수신 성공을 응답한다. 이후 처리에 실패해도 어떤 주문을 다시 확인해야 하는지 알 수 있다.
2. **Toss의 실제 거래와 대조한다.** 저장된 주문 정보로 Toss 결제를 조회하고 `orderId`, `paymentKey`, 금액을 확인한다. 승인 완료가 확인되고 내부 상태만 `APPROVING`이라면, 기존 승인 완료 로직을 재사용해 결제 `DONE`과 예매 `CONFIRMED`를 함께 반영한다.
3. **누락된 취소 건만 반영한다.** 부분 취소는 같은 주문에서 여러 번 가능하므로 주문번호만으로 중복을 판단할 수 없다. Toss 취소 거래 키와 내부 취소 이력을 대조한 뒤, 아직 반영하지 않은 건에 대해서만 환불액과 좌석 점유 상태를 갱신한다.

</details>

<details>
<summary><strong>💡 미션 후 새롭게 알게 된 점</strong></summary>

### ① `ScreeningSeat`를 추가하면서 락의 범위를 다시 생각했다

처음에는 물리 좌석인 `Seat`에 비관적 락을 걸었다. 코드 리뷰에서 “10시 상영의 A1을 예매하는 동안 13시 상영의 A1도 기다려야 하나요?”라는 지적을 받고, 잠금의 기준이 실제 예매 규칙보다 넓다는 걸 알게 됐다. 그래서 **상영 회차별 점유 상태는 `ScreeningSeat`에, 누가 어떤 좌석을 예매했는지는 `ReservationSeat`에** 남기기로 했다.

이렇게 나누니 각 코드의 이유도 분명해졌다. `(screening_id, seat_id)` 유니크 제약은 회차별 좌석 행을 하나만 만들고, 비관적 락은 그 행을 점유하려는 요청의 순서를 정한다. 취소할 때는 예매 이력을 보존하면서 점유만 해제할 수 있다. 실제로 2석 중 1석을 취소했을 때 취소한 좌석 3은 `reserved=0`, 남은 좌석 2는 `reserved=1`로 유지되는 것까지 확인했다.

### ② 결제 클라이언트를 분리하면서 트랜잭션의 경계를 이해했다

`TossPaymentClient`를 작성할 때는 처음에 URI, 헤더, 요청 본문을 어떻게 구성할지에 집중했다. 그런데 전체 취소와 부분 취소를 구현하면서 더 중요한 결정은 **누가 취소 금액을 계산하고, 어느 시점에 DB를 변경할지**라는 걸 알게 됐다.

그래서 서버가 예매 당시 좌석 가격으로 취소 금액을 계산하고, `TossPaymentClient`는 검증된 금액과 멱등키를 전달하도록 했다. `PaymentCancellationService`는 준비 → 외부 호출 → 완료 순서를 조율하고, `PaymentTransactionService`는 준비와 완료에 필요한 DB 변경을 맡는다. 이 구조에서는 네트워크 응답을 기다리는 동안 DB 락을 잡고 있을 필요가 없고, 오류가 나도 어느 단계까지 처리됐는지 추적하기 쉽다.

### ③ 결제 오류를 보면서 어디까지 성공했는지부터 확인하게 됐다

테스트 중 Toss 인증을 마쳤는데 상점으로 돌아오는 페이지가 `404`가 된 적이 있었다. 또 승인 요청이 `PAYMENT4031`로 거절됐을 때는 로그인 사용자와 주문 소유자를 맞춰 새 예매로 진행한 뒤에야 승인을 확인할 수 있었다. 같은 “결제가 안 된다”는 현상처럼 보여도, 하나는 **브라우저의 복귀 경로**, 다른 하나는 **서버의 주문 소유자 검증**에서 발생한 문제였다.

이 경험을 결제 복구 설계에도 적용했다. 승인 응답을 못 받았다는 사실만으로는 Toss가 실제로 승인했는지 판단할 수 없다. 그래서 결과가 불명확한 주문은 PG 거래와 대조하고, 이미 처리된 거래는 다시 반영하지 않는 정책이 필요하다고 정리했다. 이번에는 동일 승인 요청의 `approvedAt`이 유지되고, 동일한 부분 취소 요청을 다시 보내도 누적 취소 금액이 15,000원으로 유지되는 것까지 확인했다. **Toss 승인 직후 내부 DB 저장만 실패하는 상황을 재현하고 복구하는 것**은 다음 검증 과제로 남았다.

</details>

# 5주차

<details>
<summary><strong>Docker로 애플리케이션과 DB 실행</strong></summary>

이번 미션에서는 Spring Boot 애플리케이션을 이미지로 만들고, MySQL과 함께 EC2에서 실행할 수 있도록 구성했다. 개발 PC와 서버에서 같은 실행 환경을 사용하고, 배포할 때마다 서버에서 Gradle 빌드를 반복하지 않는 것이 목표였다.

| 파일 | 역할 |
| --- | --- |
| `Dockerfile` | Java 21 JDK 이미지에서 `bootJar`를 만들고, Java 21 JRE 이미지에 결과물만 복사한다. |
| `compose.yaml` | 앱과 MySQL 8.4를 함께 실행한다. 앱은 `db` 호스트로 MySQL에 접속하고, DB가 `healthy`가 된 뒤 시작한다. |
| `compose.deploy.yaml` | EC2에서는 서버에서 빌드하는 대신 GitHub Container Registry(GHCR)에 게시된 앱 이미지를 사용한다. |

DB 데이터는 `mysql_data` 볼륨에 저장했다. 앱과 DB의 접속 정보, JWT 시크릿, Toss 테스트 키는 EC2의 `.env`에서 읽는다. `.env`는 Git에 올리지 않고 서버에만 보관한다.

로컬에서는 `docker compose up -d --build`로 앱과 DB를 함께 빌드·실행할 수 있다. EC2에서는 GitHub Actions가 빌드한 이미지를 내려받아 `compose.yaml`과 `compose.deploy.yaml`을 함께 적용한다.

</details>

<details>
<summary><strong>GitHub Actions로 테스트·이미지 게시·자동 배포</strong></summary>

`.github/workflows/ci-cd.yml`에 `test → publish → deploy` 작업을 연결했다.

| 실행 조건 | 수행 작업 |
| --- | --- |
| Pull Request | MySQL 8.4 서비스와 Java 21 환경을 준비하고 `./gradlew test --no-daemon`을 실행한다. |
| `Mymyseoyoung` 브랜치에 push | 테스트가 성공하면 Docker 이미지를 빌드해 GHCR에 커밋 SHA와 `latest` 태그로 게시한다. |
| 이미지 게시 성공 | `production` 환경의 SSH 정보를 이용해 EC2에 Compose 파일을 복사하고, 해당 SHA의 이미지를 내려받아 앱을 실행한다. |

배포 작업은 EC2 안에서 `http://localhost:8080/api/movies`를 반복 호출해 앱의 응답을 확인한다. 응답하지 않으면 앱 로그를 출력하고 작업을 실패로 처리한다. 이 확인을 통해 **컨테이너 시작 명령의 성공**과 **애플리케이션이 요청을 처리할 준비가 됨**을 구분했다.

EC2 접근에 필요한 `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`는 GitHub의 `production` Environment secrets에 저장했다. 실제 DB 비밀번호와 JWT·Toss 키는 EC2의 `.env`에 저장해 워크플로 파일에 넣지 않았다.

</details>

<details>
<summary><strong>예매 취소 코드 리뷰 반영</strong></summary>

결제 승인 도중 예매를 직접 취소하면 Toss에서는 결제가 성공했는데 내부 예매는 취소되는 상황이 생길 수 있다는 리뷰를 받았다. `ReservationService.cancelReservation()`에서 **예매 행 → 결제 행** 순서로 잠그고, 결제 상태가 `READY`일 때만 직접 취소하도록 수정했다. `APPROVING` 등 다른 상태라면 좌석을 반환하기 전에 요청을 거절한다.

`ReservationCancellationPaymentStateTest`에서는 `APPROVING` 결제의 예매 취소가 거절되고, 좌석 점유 변경 코드까지 진행하지 않는지 확인한다.

</details>

<details>
<summary><strong>EC2 배포 중 확인한 문제</strong></summary>

| 증상 | 확인한 원인 | 조치 |
| --- | --- | --- |
| EC2에서 `docker compose up -d --build`가 `Killed`로 종료 | 약 1GB 메모리에서 Gradle과 Docker 빌드를 동시에 실행하다 OOM 발생 | 이미지 빌드를 GitHub Actions로 옮기고 EC2는 이미지만 내려받도록 변경 |
| MySQL이 계속 재시작 | `.env`의 `DB_USERNAME=root`는 MySQL 이미지의 일반 사용자 설정과 충돌 | 별도의 일반 DB 사용자로 변경 |
| DB가 `Restarting (137)`이고 앱이 DB에 연결하지 못함 | 커널 로그에서 `mysqld`가 OOM으로 종료된 것을 확인 | EC2 메모리를 약 2GB로 늘린 뒤 DB가 `healthy`인 것을 확인 |
| DB가 정상인데 앱이 재시작하며 `curl` 연결이 끊김 | `JWT_SECRET`에 역슬래시가 들어 있어 Base64 디코딩 실패 | `.env`의 JWT 값을 유효한 Base64 값으로 교체하고 앱 컨테이너를 재생성하도록 조치 |

현재 확인한 배포 결과는 **GHCR 이미지 게시, EC2 이미지 내려받기, MySQL `healthy` 상태**까지다. JWT 설정을 수정한 뒤 앱의 API 응답과 GitHub Actions 배포 성공 여부는 다시 확인해야 한다. 상세한 서버 준비와 실행 명령은 [`deploy/README.md`](deploy/README.md)에 정리했다.

</details>

<details>
<summary><strong>💡 미션 후 새롭게 알게 된 점</strong></summary>

### ① 이미지 빌드와 컨테이너 실행에 필요한 자원이 다르다

EC2에서 빌드가 OOM으로 종료돼 빌드 위치를 GitHub Actions로 옮겼다. 하지만 이후에도 앱과 MySQL을 함께 실행할 메모리가 부족해 `mysqld`가 종료됐다. **CI에서 빌드하면 서버의 빌드 부담은 줄지만, 실행 중인 컨테이너의 메모리까지 줄어드는 것은 아니다.**

### ② `Up`만으로 앱의 정상 동작을 판단할 수 없다

Docker 목록에 앱이 `Up`으로 표시돼도 Spring 초기화 도중 예외가 발생하면 곧 재시작할 수 있었다. `curl` 응답, 재시작 횟수, 앱 로그를 함께 확인해야 어느 단계에서 실패했는지 알 수 있었다.

### ③ 환경변수 형식도 배포의 일부다

DB가 정상화된 뒤에는 `JWT_SECRET`에 들어간 문자 하나 때문에 앱이 시작하지 못했다. JWT 시크릿은 코드에서 Base64로 디코딩하므로 유효한 Base64 문자열이어야 한다. `.env`를 수정한 뒤에는 기존 컨테이너를 재시작하는 것만으로 변경이 반영되지 않아 앱 컨테이너를 재생성해야 한다는 점도 확인했다.

</details>
