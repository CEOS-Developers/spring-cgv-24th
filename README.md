# CGV 서비스 데이터 모델링

## 1. 서비스 개요

CGV 클론 코딩 서비스는 사용자가 영화관과 영화를 조회하고, 관심 있는 영화관과 영화를 찜하며, 상영 회차의 좌석을 예매하거나 예매를 취소할 수 있는 서비스다. 영화관별 매점에서 상품을 구매하는 기능도 제공한다.

| 기능 | 관련 모델 |
| --- | --- |
| 영화관 조회 | cinema, auditorium, auditorium_type |
| 영화관 찜 | user, cinema_favorite, cinema |
| 영화 조회 | movie, screening |
| 영화 예매·취소 | user, screening, reservation, reservation_seat, seat |
| 영화 찜 | user, movie_favorite, movie |
| 매점 구매 | user, cinema, product, cinema_stock, purchase, purchase_item |

## 2. 요구사항과 설계 가정

### 미션 요구사항

- 모든 영화관에는 일반관과 특별관이 존재한다.
- 상영관 종류가 같으면 좌석 구성도 동일하다.
- 좌석은 통로나 중간 공백이 없는 직사각형 형태다.
- 모든 영화관의 매점 메뉴는 같으며, 재고는 영화관별로 관리한다.
- 재고는 항상 1 이상이다.
- 예매 취소를 제공하고 매점 구매 환불은 제공하지 않는다.

### 현재 모델을 설명하기 위한 가정

- 한 예매는 한 상영 회차에 대한 것이며 여러 좌석을 포함할 수 있다.
- 취소는 예매 전체에 적용하고, 예매 기록은 삭제하지 않고 상태를 변경한다.
- 상품 구매는 한 영화관에서 이루어지며 여러 상품을 포함할 수 있다.
- 상품 가격은 모든 영화관이 공통이고, 상영 회차 안에서는 좌석당 가격이 동일하다.
- 찜 해제는 해당 찜 기록을 삭제하는 방식으로 시작한다.
- 현재 ERD는 재고 수량을 1 이상으로 표시한다.
    - 판매 후 수량이 0이 되는 경우 생각해봐야함

## 3. 설계 및 모델링 과정

### 3.1. 사용자 행동에서 필요한 대상을 도출

먼저 기능을 구체적인 상황으로 풀어 필요한 정보를 찾았다. 예를 들어 “사용자가 특정 영화관의 상영관에서 특정 시간에 상영하는 영화의 좌석을 예매한다”는 행동에는 사용자, 영화관, 상영관, 영화, 상영 일정, 좌석, 예매가 필요하다.

대상 자체의 정보와 행동으로 발생하는 기록을 구분했다. 영화 이름은 영화의 속성이지만, 누가 어떤 회차를 예매했는지는 별도의 예매 기록이다. 찜과 구매도 각각 기록을 관리할 수 있도록 엔티티로 분리했다.

### 3.2. 양쪽의 개수를 확인해 관계 결정

영화관 하나는 여러 상영관을 가지지만 실제 상영관 하나는 한 영화관에 속하므로 1:N 관계로 설계했다. 영화 하나는 여러 회차에 상영되지만 회차 하나는 한 영화를 상영하므로 영화와 상영 일정도 1:N 관계다.

사용자는 여러 영화를 찜할 수 있고 영화도 여러 사용자에게 찜될 수 있다. 이러한 N:M 관계는 movie_favorite와 같은 중간 엔티티를 사용해 두 개의 1:N 관계로 표현했다.

### 3.3. 공통 정보와 개별 정보를 분리

- 영화 자체의 정보와 특정 시간·장소의 상영 정보를 movie와 screening으로 분리했다.
- 상영관 종류의 공통 좌석 규격과 실제 지점의 상영관을 auditorium_type과 auditorium으로 분리했다.
- 모든 영화관이 공유하는 상품 정보와 지점별 재고를 product와 cinema_stock으로 분리했다.
- 예매와 구매는 공통 정보를 가진 상위 기록과 여러 항목을 가진 상세 기록으로 분리했다.

## 4. ERD

CGV ERD

![CGV ERD](/cgv-erd.png)

CGV ERD

## 5. 모델별 설명

| 모델 | 현재 ERD의 필드 | 역할과 설계 이유 |
| --- | --- | --- |
| user | id, name | 찜·예매·구매의 주체. 
이번 주에는 인증 정보를 제외한 최소 정보로 구성 |
| cinema | id, name, address, region | 영화관 지점의 정보를 관리
region은 지역별 조회에 사용 |
| auditorium_type | id, name, category, row_count, column_count | 일반관·특별관 종류와 공통 좌석 규격을 관리
category는 NORMAL 또는 SPECIAL을 나타냄 |
| auditorium | id, cinema_id, auditorium_type_id, name | 특정 지점의 실제 상영관 |
| seat | id, auditorium_id, row_number, column_number | 실제 상영관 안의 좌석 위치
예매 여부는 회차마다 다르므로 좌석 자체에 저장하지 않는다 |
| movie | id, name, release_date | 영화 이름과 개봉일을 관리
상영 장소와 시각은 screening에서 관리 |
| screening | id, auditorium_id, movie_id, starts_at, ends_at, price | 영화·상영관·시각을 결합한 상영 회차
price는 현재 해당 회차의 좌석당 가격 |
| cinema_favorite | id, cinema_id, user_id, created_at | 사용자와 영화관의 찜 관계 및 찜한 시각을 기록 |
| movie_favorite | id, user_id, movie_id, created_at | 사용자와 영화의 찜 관계 및 찜한 시각을 기록 |
| reservation | id, screening_id, user_id, status, reserved_at, cancelled_at | 사용자와 회차를 연결하는 예매 내역
CONFIRMED와 CANCELLED로 상태를 구분하며 취소 전 cancelled_at은 NULL |
| reservation_seat | id, seat_id, reservation_id | 한 예매에 포함된 좌석을 기록
여러 좌석을 예매하면 상세 행이 여러 개 생성 |
| product | id, name, price, description | 모든 영화관이 공유하는 메뉴
price는 현재 상품 단가이며 description은 선택 정보 |
| cinema_stock | id, cinema_id, product_id, quantity | 특정 영화관의 특정 상품 재고를 관리
동일 상품도 지점마다 다른 수량을 가짐 |
| purchase | id, user_id, cinema_id, purchased_at | 누가 어느 영화관에서 언제 구매했는지 기록
구매 지점은 재고 차감 대상을 결정 |
| purchase_item | id, product_id, purchase_id, quantity, unit_price | 구매 상품과 수량, 구매 당시 단가를 기록
현재 상품 가격이 바뀌어도 기존 구매 금액을 계산할 수 있다. |

## 6. 주요 관계

| 1쪽 모델 | N쪽 모델 | N쪽의 FK |
| --- | --- | --- |
| cinema | auditorium | cinema_id |
| auditorium_type | auditorium | auditorium_type_id |
| auditorium | seat | auditorium_id |
| auditorium | screening | auditorium_id |
| movie | screening | movie_id |
| user / cinema | cinema_favorite | user_id / cinema_id |
| user / movie | movie_favorite | user_id / movie_id |
| user / screening | reservation | user_id / screening_id |
| reservation / seat | reservation_seat | reservation_id / seat_id |
| cinema / product | cinema_stock | cinema_id / product_id |
| user / cinema | purchase | user_id / cinema_id |
| purchase / product | purchase_item | purchase_id / product_id |

## 7. 무결성 규칙과 구현 계획

### 7.1. 중복 방지 제약

| 테이블 | 적용할 UNIQUE | 목적 |
| --- | --- | --- |
| auditorium_type | (name) | 상영관 종류 이름 중복 방지 |
| auditorium | (cinema_id, name) | 같은 지점 안에서 상영관 이름 중복 방지 |
| seat | (auditorium_id, row_number, column_number) | 같은 상영관 안에서 좌석 위치 중복 방지 |
| cinema_stock | (cinema_id, product_id) | 지점·상품별 재고 기록을 하나로 유지 |
| cinema_favorite | (user_id, cinema_id) | 같은 영화관 중복 찜 방지 |
| movie_favorite | (user_id, movie_id) | 같은 영화 중복 찜 방지 |
| reservation_seat | (reservation_id, seat_id) | 한 예매 안에서 좌석 중복 방지 |
| purchase_item | (purchase_id, product_id) | 한 구매 안에서 동일 상품을 한 행으로 합치는 정책을 선택할 경우 적용 |

복합 UNIQUE는 각 컬럼에 따로 UNIQUE를 설정하는 것과 다르다. 예를 들어 auditorium.name만 UNIQUE이면 서로 다른 지점에서도 같은 “1관”이라는 이름을 사용할 수 없다.

### 7.2. 값과 상태 검증

- 좌석 규격과 좌석 행·열 번호는 양수다. 실제 좌석은 해당 종류의 규격 범위 안에 있어야 한다.
- 종류별 행 수 × 열 수만큼 모든 위치의 좌석이 있어야 한다.
- 상품 가격, 회차 가격, 구매 당시 단가는 0 이상이고 구매 수량은 1 이상이다.
- 상영 종료 시각은 시작 시각보다 뒤여야 한다.
- 같은 상영관의 상영 일정은 서로 겹치면 안 된다.


# CGV API 명세

## 접속 정보

- Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 공통 성공 응답

모든 성공 응답은 다음 형식을 사용합니다.

```json
{
  "result": {},
  "resultCode": 200,
  "resultMsg": "SELECT SUCCESS"
}
```

| 작업 | HTTP 상태 | `resultMsg` |
| --- | --- | --- |
| 등록 | `201 Created` | `INSERT SUCCESS` |
| 조회 | `200 OK` | `SELECT SUCCESS` |
| 삭제·취소 | `200 OK` | `DELETE SUCCESS` |

등록 API의 `result`에는 생성된 데이터의 ID가 반환됩니다. 삭제 API의 `result`는 `null`입니다.

## 공통 오류 응답

```json
{
  "status": 404,
  "divisionCode": "M001",
  "resultMsg": "존재하지 않는 영화입니다.",
  "errors": [],
  "reason": null
}
```

DTO 검증에 실패하면 `errors`에 잘못된 필드 정보가 포함됩니다.

```json
{
  "status": 400,
  "divisionCode": "G011",
  "resultMsg": "요청 값이 유효하지 않습니다.",
  "errors": [
    {
      "field": "name",
      "value": "",
      "reason": "영화 이름은 필수입니다."
    }
  ],
  "reason": null
}
```

## 전체 API 목록

| 도메인 | Method | URL | 기능 |
| --- | --- | --- | --- |
| 영화 | `POST` | `/api/movies` | 영화 등록 |
| 영화 | `GET` | `/api/movies` | 영화 목록 조회 |
| 영화 | `GET` | `/api/movies/{movieId}` | 영화 상세 조회 |
| 영화 | `DELETE` | `/api/movies/{movieId}` | 영화 삭제 |
| 영화관 | `POST` | `/api/cinemas` | 영화관 등록 |
| 영화관 | `GET` | `/api/cinemas` | 영화관 목록 조회 |
| 영화관 | `GET` | `/api/cinemas/{cinemaId}` | 영화관 상세 조회 |
| 영화관 | `DELETE` | `/api/cinemas/{cinemaId}` | 영화관 삭제 |
| 상영관 | `POST` | `/api/cinemas/{cinemaId}/auditoriums` | 상영관과 좌석 등록 |
| 상영관 | `GET` | `/api/cinemas/{cinemaId}/auditoriums` | 영화관별 상영관 목록 조회 |
| 상영정보 | `POST` | `/api/screenings` | 상영 회차 등록 |
| 상영정보 | `GET` | `/api/cinemas/{cinemaId}/screenings` | 영화관별 상영정보 조회 |
| 좌석 | `GET` | `/api/screenings/{screeningId}/seats` | 상영 회차별 좌석 상태 조회 |
| 영화 찜 | `POST` | `/api/users/{userId}/favorite-movies/{movieId}` | 영화 찜 등록 |
| 영화 찜 | `GET` | `/api/users/{userId}/favorite-movies` | 영화 찜 목록 조회 |
| 영화 찜 | `DELETE` | `/api/users/{userId}/favorite-movies/{movieId}` | 영화 찜 삭제 |
| 영화관 찜 | `POST` | `/api/users/{userId}/favorite-cinemas/{cinemaId}` | 영화관 찜 등록 |
| 영화관 찜 | `GET` | `/api/users/{userId}/favorite-cinemas` | 영화관 찜 목록 조회 |
| 영화관 찜 | `DELETE` | `/api/users/{userId}/favorite-cinemas/{cinemaId}` | 영화관 찜 삭제 |
| 예매 | `POST` | `/api/users/{userId}/reservations` | 좌석 예매 |
| 예매 | `GET` | `/api/users/{userId}/reservations` | 사용자 예매 목록 조회 |
| 예매 | `GET` | `/api/users/{userId}/reservations/{reservationId}` | 예매 상세 조회 |
| 예매 | `DELETE` | `/api/users/{userId}/reservations/{reservationId}` | 예매 취소 |
| 매점 상품 | `GET` | `/api/cinemas/{cinemaId}/products` | 영화관별 상품·재고 조회 |
| 매점 구매 | `POST` | `/api/users/{userId}/purchases` | 매점 상품 구매 |
| 매점 구매 | `GET` | `/api/users/{userId}/purchases` | 사용자 구매 목록 조회 |
| 매점 구매 | `GET` | `/api/users/{userId}/purchases/{purchaseId}` | 구매 상세 조회 |


# 스터디 질문 답

## 1. 로딩 전략과 N+1 문제

### 1) `@JoinColumn`을 명시하지 않으면 어떻게 되는가?

`@ManyToOne`과 같은 연관관계에서 `@JoinColumn`을 생략하면 JPA가 기본 규칙에 따라 외래 키 컬럼명을 자동으로 생성한다.

기본 이름은 일반적으로 다음과 같다.

```
연관관계 필드명_참조하는 테이블의 PK 컬럼명
```

예를 들어 `Member`의 연관관계 필드가 `team`이고 `Team`의 PK 컬럼이 `id`라면 외래 키 컬럼명은 일반적으로 `team_id`가 된다.

```java
@ManyToOne
private Team team;
```

```
MEMBER.team_id → TEAM.id
```

다만 실제 컬럼명은 Hibernate나 Spring Boot의 네이밍 전략에 따라 달라질 수 있다. 따라서 DB 컬럼명을 명확하게 관리하려면 `@JoinColumn(name = "team_id")`처럼 직접 지정하는 것이 좋다.

---

### 2) 양방향 매핑이 항상 좋은가?

양방향 매핑이 항상 좋은 것은 아니다. 양쪽 엔티티에서 서로를 탐색해야 할 필요가 있을 때만 사용하는 것이 좋다.

양방향 매핑을 사용하면 `Member → Team`, `Team → Member`처럼 양쪽 방향으로 조회할 수 있다는 장점이 있다. 그러나 다음과 같은 단점도 발생한다.

- 연관관계의 주인과 비주인을 구분해야 한다.
- 양쪽 객체를 모두 동기화하지 않으면 객체 상태가 불일치할 수 있다.
- JSON 직렬화 시 순환 참조가 발생할 수 있다.
- 연관관계와 비즈니스 로직이 복잡해진다.
- 예상하지 못한 지연 로딩이나 N+1 문제가 발생할 수 있다.

따라서 기본적으로는 외래 키가 있는 쪽에서 단방향 `@ManyToOne`을 사용하고, 반대 방향 조회가 실제로 필요할 때만 `@OneToMany(mappedBy = "...")`를 추가하는 것이 좋다.

양방향 매핑을 사용한다면 다음과 같은 연관관계 편의 메서드로 양쪽 객체를 함께 변경해야 한다.

```java
public void changeTeam(Team team) {
    if (this.team != null) {
        this.team.getMembers().remove(this);
    }

    this.team = team;

    if (team != null) {
        team.getMembers().add(this);
    }
}
```

---

### 3) PK 참조와 UUID 참조 중 어떤 방식을 사용해야 하는가?

일반적으로 DB 내부에서는 `Long` 타입의 PK를 사용하고, 외부 API에는 별도의 UUID를 노출하는 방식을 많이 사용한다.

```java
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // DB 내부 식별자

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId; // 외부 노출용 식별자
}
```

각 방식의 특징:

| 구분 | Long PK | UUID |
| --- | --- | --- |
| 크기 | 작다 | 상대적으로 크다 |
| 인덱스 성능 | 좋다 | 상대적으로 불리할 수 있다 |
| 생성 방식 | DB에서 순차 생성한다 | 애플리케이션에서도 생성할 수 있다 |
| 값 추측 | 쉽다 | 어렵다 |
| 외부 노출 | 내부 데이터 규모가 드러날 수 있다 | 외부 노출에 적합하다 |

따라서 엔티티 간 외래 키 관계에는 내부용 `Long PK`를 사용하고, API 요청과 응답에는 `UUID publicId`를 사용하는 방식이 성능과 보안 측면에서 균형이 좋다.

---

### 4) `Team.members`만 수정하면 DB에 반영되는가?

DB에는 반영되지 않는다.

```java
@OneToMany(mappedBy = "team")
private List<Member> members;
```

`mappedBy`가 설정된 `Team.members`는 연관관계의 주인이 아닌 읽기 전용 방향이다. 실제 외래 키인 `team_id`는 `Member` 테이블에 있으므로, 연관관계의 주인인 `Member.team`을 수정해야 DB의 외래 키가 변경된다.

```java
team.getMembers().add(member); // 이것만으로는 DB의 team_id가 변경되지 않는다.
member.setTeam(team);          // 연관관계의 주인을 변경한다.
```

따라서 연관관계 편의 메서드를 통해 양쪽을 함께 수정하는 것이 안전하다.

---

### 5) `mappedBy` 없이 양쪽 모두에 `@JoinColumn`을 사용하면 어떻게 되는가?

JPA는 두 필드를 하나의 양방향 관계로 인식하지 않고, 각각 독립적으로 외래 키를 관리하는 관계로 인식할 수 있다.

```java
class Member {

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;
}

class Team {

    @OneToMany
    @JoinColumn(name = "team_id")
    private List<Member> members;
}
```

이 경우 양쪽 모두 동일한 `MEMBER.team_id`를 변경하려고 하므로 다음과 같은 문제가 발생할 수 있다.

- 불필요한 추가 `UPDATE` 쿼리가 실행될 수 있다.
- 두 객체의 값이 다르면 서로 다른 외래 키 값을 저장하려고 한다.
- 설정과 Hibernate 버전에 따라 컬럼 중복 매핑 오류가 발생할 수 있다.
- 어떤 객체의 상태를 기준으로 관계를 관리해야 하는지 불명확해진다.

따라서 외래 키가 있는 `Member.team`을 연관관계의 주인으로 설정하고, `Team.members`에는 다음과 같이 `mappedBy`를 지정해야 한다.

```java
@OneToMany(mappedBy = "team")
private List<Member> members;
```

---

### 6) Proxy란 무엇인가?

프록시는 실제 객체에 바로 접근하지 않고 그 객체를 대신해 자리를 차지하는 대리 객체이다.

JPA에서 지연 로딩을 사용하면 연관된 엔티티를 즉시 DB에서 조회하지 않고, 우선 해당 엔티티의 식별자를 가진 Hibernate 프록시 객체를 넣어둔다.

```java
Member member = em.find(Member.class, memberId);
Team team = member.getTeam(); // 아직 프록시일 수 있다.
team.getName();               // 실제 데이터가 필요해지는 시점에 SELECT가 실행된다.
```

프록시 객체는 실제 엔티티를 상속받아 만들어지며 식별자와 영속성 컨텍스트에 대한 정보를 가지고 있다. 실제 데이터에 접근하면 프록시가 초기화되면서 DB 조회가 발생한다.

프록시를 초기화할 때 영속성 컨텍스트가 이미 종료되었다면 `LazyInitializationException`이 발생할 수 있다.

---

### 7) Proxy와 N+1 문제는 어떤 관계인가?

지연 로딩에서는 연관된 엔티티를 프록시로 보관하고, 실제 데이터를 사용하는 순간마다 프록시를 초기화한다.

예를 들어 Member N명을 먼저 한 번의 쿼리로 조회한 다음 각 Member의 Team을 조회하면 다음과 같이 동작할 수 있다.

```
Member 목록 조회: 1번
각 Member의 Team 프록시 초기화: 최대 N번
전체 쿼리: 1 + N번
```

```java
List<Member> members = memberRepository.findAll();

for (Member member : members) {
    System.out.println(member.getTeam().getName());
}
```

따라서 프록시 자체가 N+1 문제의 원인은 아니지만, 반복문에서 여러 프록시를 하나씩 초기화하면 N+1 문제가 발생할 수 있다.

동일한 Team을 여러 Member가 참조하고 있고 해당 Team이 1차 캐시에 이미 존재한다면 실제 추가 쿼리 수는 줄어들 수 있다.

---

### 8) Hibernate Proxy와 Spring AOP Proxy의 차이는 무엇인가?

| 구분 | Hibernate Proxy | Spring AOP Proxy |
| --- | --- | --- |
| 목적 | JPA 지연 로딩 | 공통 부가 기능 적용 |
| 대상 | 엔티티 | Spring Bean |
| 대표 기능 | 연관 엔티티를 사용할 때 DB 조회 | 트랜잭션, 로깅, 보안 등 |
| 생성 방식 | 주로 엔티티의 하위 클래스 생성 | JDK 동적 프록시 또는 클래스 기반 프록시 |
| 동작 시점 | 연관된 실제 데이터에 접근할 때 | 프록시를 통해 메서드를 호출할 때 |

Hibernate Proxy는 실제 엔티티 조회를 미루기 위한 것이고, Spring AOP Proxy는 메서드 호출 앞뒤에 공통 로직을 적용하기 위한 것이다.

예를 들어 `@Transactional`은 Spring AOP Proxy를 통해 트랜잭션을 시작하고 종료한다.

---

### 9) 양방향 `@OneToOne`과 `nullable=true`에서 지연 로딩 프록시 문제가 발생하는 이유는 무엇인가?

`@OneToOne` 양방향 관계에서 외래 키가 없는 반대편 엔티티는 자신의 테이블만 조회해서는 연관된 객체가 존재하는지 알 수 없다.

특히 외래 키가 `nullable=true`이면 연관된 엔티티가 있을 수도 있고 없을 수도 있다. 그런데 프록시를 만들려면 최소한 대상 엔티티의 식별자를 알아야 한다.

따라서 Hibernate는 다음 두 가지를 판단하기 위해 추가 조회를 수행할 수 있다.

- 연관된 엔티티가 실제로 존재하는가?
- 존재한다면 해당 엔티티의 식별자는 무엇인가?

이 때문에 양방향 `@OneToOne`의 연관관계 주인이 아닌 방향에서는 `LAZY`를 설정해도 즉시 조회가 발생하거나 지연 로딩이 제대로 적용되지 않을 수 있다.

해결 방법으로는 다음을 고려할 수 있다.

- 불필요한 양방향 매핑을 제거한다.
- 외래 키를 실제 조회가 많이 일어나는 엔티티에 배치한다.
- `optional = false`처럼 연관관계가 반드시 존재하도록 설계한다.
- 공유 PK 구조라면 `@MapsId`를 사용한다.
- 조회 API에 맞춰 Fetch Join이나 DTO 조회를 사용한다.
- 필요한 경우 Hibernate bytecode enhancement를 검토한다.

---

### 10) 항상 지연 로딩이 좋은가?

지연 로딩에도 N+1 문제와 `LazyInitializationException` 같은 단점이 있으므로 항상 좋은 것은 아니다.

하지만 엔티티의 기본 FetchType은 가능한 한 `LAZY`로 설정하는 것이 권장된다. `EAGER`는 특정 조회에서 연관 데이터를 사용하지 않더라도 항상 조회하며, JPQL 목록 조회에서는 예상하지 못한 추가 쿼리와 N+1 문제를 발생시킬 수 있기 때문이다.

```java
@ManyToOne(fetch = FetchType.LAZY)
private Team team;
```

연관 데이터가 필요한 조회에서는 Fetch Join이나 `@EntityGraph`를 사용하여 해당 쿼리에서만 함께 가져오는 방식이 좋다.

즉, 엔티티의 기본 연관관계는 `LAZY`로 설정하고, 연관 데이터가 필요한 조회에서만 Fetch Join이나 EntityGraph로 명시적으로 함께 조회하는 것이 좋다.

---

### 11) Fetch Join과 페이징을 함께 사용하면 어떤 문제가 발생하는가?

`@ManyToOne`과 같은 To-One 관계의 Fetch Join은 행이 크게 증가하지 않으므로 일반적으로 페이징과 함께 사용할 수 있다.

문제는 `@OneToMany`와 같은 컬렉션 Fetch Join이다.

```java
select t
from Team t
join fetch t.members
```

Team 하나에 Member가 여러 명이면 SQL 결과에서는 Team 데이터가 Member 수만큼 중복된다. DB는 Team이 아니라 SQL 결과 행을 기준으로 페이징하기 때문에 다음 문제가 발생한다.

- 한 페이지의 Team 개수가 예상과 달라질 수 있다.
- 컬렉션 일부만 조회될 수 있다.
- Hibernate가 전체 결과를 메모리로 가져온 뒤 애플리케이션에서 페이징할 수 있다.
- 데이터가 많으면 메모리 사용량과 응답 시간이 크게 증가한다.

---

### 12) 싱글톤인 `SimpleJpaRepository`가 EntityManager를 주입받아도 안전한 이유는 무엇인가?

`SimpleJpaRepository`에 주입되는 객체는 일반적으로 실제 EntityManager 하나가 아니라 Spring이 만든 공유 EntityManager 프록시이다.

```
SimpleJpaRepository
        ↓
공유 EntityManager Proxy
        ↓
현재 트랜잭션에 연결된 실제 EntityManager
```

Repository는 싱글톤이지만 EntityManager 프록시는 메서드가 호출될 때 현재 스레드와 트랜잭션에 연결된 실제 EntityManager를 찾아 위임한다.

따라서 여러 요청이 같은 Repository 객체를 사용하더라도 실제로는 각 트랜잭션에 맞는 EntityManager가 사용된다. 스레드에 안전하지 않은 실제 EntityManager 하나를 모든 요청이 직접 공유하는 구조가 아니다.

---

### 13) Fetch Join에서 `distinct`를 사용하지 않으면 어떤 문제가 발생하는가?

일대다 컬렉션을 Fetch Join하면 SQL 결과에서 부모 엔티티가 자식 수만큼 중복된다.

예를 들어 Team 하나에 Member가 3명이면 SQL 결과에는 동일한 Team이 3개의 행으로 나타난다.

```java
select t
from Team t
join fetch t.members
```

---

## 2. ORM과 JPA

### 14) Dirty Checking이 모든 컬럼을 UPDATE하는 것은 개선해야 하는가?

Hibernate는 기본적으로 변경된 엔티티에 대해 변경되지 않은 컬럼까지 포함한 정적 UPDATE SQL을 생성할 수 있다.

```sql
UPDATE member
SET username = ?, email = ?, age = ?
WHERE id = ?
```

여기서 모든 엔티티를 무조건 UPDATE한다는 뜻은 아니다. Dirty Checking으로 변경이 감지된 엔티티의 모든 수정 가능 컬럼을 UPDATE한다는 의미이다.

모든 컬럼을 포함하면 SQL 형태가 일정하기 때문에 다음과 같은 장점도 있다.

- SQL 재사용과 실행 계획 캐싱에 유리하다.
- JDBC Batch 처리를 적용하기 쉽다.
- 매번 서로 다른 UPDATE SQL을 생성하지 않아도 된다.

따라서 컬럼 수가 적은 일반적인 엔티티라면 반드시 개선할 필요는 없다.

컬럼이 매우 많거나 일부 컬럼만 자주 변경된다면 Hibernate의 `@DynamicUpdate`를 사용할 수 있다.

```java
@Entity
@DynamicUpdate
public class Member {
}
```

`@DynamicUpdate`는 실제로 변경된 컬럼만 UPDATE SQL에 포함한다. 하지만 변경된 컬럼 조합마다 SQL이 달라져 SQL 캐싱이나 배치 처리에 불리할 수 있으므로 성능 측정 후 적용해야 한다.

그 밖에도 다음 방법을 사용할 수 있다.

- 자주 변경되는 필드를 별도 엔티티와 테이블로 분리한다.
- 변경하면 안 되는 필드에 `updatable = false`를 설정한다.
- 특정 컬럼만 수정하는 JPQL Update Query를 사용한다.
- 동시성 문제가 있다면 `@Version`을 이용한 낙관적 락을 적용한다.

---

### 15) Flush는 언제 발생하는가?

Flush는 영속성 컨텍스트의 변경 내용을 DB에 동기화하는 과정이다.

주요 발생 시점은 다음과 같다.

1. 직접 호출할 때 발생한다.

```java
entityManager.flush();
```

1. 트랜잭션을 Commit하기 직전에 발생한다.

```java
transaction.commit();
```

1. 기본 `FlushModeType.AUTO` 상태에서 JPQL 쿼리를 실행하기 전에 발생할 수 있다.

JPQL 조회 결과가 영속성 컨텍스트의 변경 내용과 달라지는 것을 방지하기 위해 필요한 경우 먼저 Flush한다.

1. Spring Data JPA의 `flush()` 또는 `saveAndFlush()`를 호출할 때 발생한다.

```java
memberRepository.saveAndFlush(member);
```

Flush는 SQL을 DB에 전달하는 것이며 트랜잭션을 Commit하는 것은 아니다. 따라서 Flush 이후에도 트랜잭션이 롤백되면 변경 내용은 최종적으로 DB에 저장되지 않는다.

---

### 16) 영속성 컨텍스트와 EntityManager는 항상 1:1인가?

항상 1:1이라고 단정할 수는 없다.

일반적인 애플리케이션 관리 방식에서는 하나의 실제 EntityManager가 하나의 영속성 컨텍스트를 관리한다. 하지만 Spring에서 주입받는 EntityManager는 실제 EntityManager가 아닌 프록시일 수 있다.

이 프록시는 실행 중인 트랜잭션에 따라 서로 다른 실제 EntityManager와 영속성 컨텍스트에 연결된다.

또한 확장 영속성 컨텍스트인 `PersistenceContextType.EXTENDED`를 사용하면 하나의 영속성 컨텍스트가 여러 트랜잭션에 걸쳐 유지될 수도 있다.

따라서 일반적인 실제 EntityManager 기준으로는 1:1에 가깝지만, Spring의 프록시와 영속성 컨텍스트 생명주기까지 고려하면 항상 고정된 1:1 관계는 아니다.

---

### 17) EntityManager와 트랜잭션은 항상 1:1인가?

항상 1:1은 아니다.

다음과 같은 경우에는 1:1이 아닐 수 있다.

- 하나의 EntityManager가 순차적으로 여러 트랜잭션에 사용될 수 있다.
- 확장 영속성 컨텍스트는 여러 트랜잭션에 걸쳐 유지될 수 있다.
- 조회 작업에서는 EntityManager가 트랜잭션 없이 존재할 수도 있다.
- JTA 환경에서는 하나의 트랜잭션에 여러 Persistence Unit의 EntityManager가 참여할 수 있다.