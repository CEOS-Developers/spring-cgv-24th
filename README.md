

# 1. DB 설계 설명
https://www.erdcloud.com/d/BdyttrzkdozWZAZPe

## 1-1. 테이블별 역할

| 테이블 | 역할 |
|---|---|
| member | 회원 |
| theater | 영화관 |
| screen | 상영관 (영화관에 속함) |
| screening | 상영 회차 (영화 + 상영관 + 시간) |
| seat | 좌석 (상영 회차에 속함) |
| reservation | 좌석 예약 |
| movie | 영화 |
| store | 매장 (영화관에 속함) |
| menu | 메뉴 |
| menu_stock | 매장별 메뉴 재고 |
| orders / order_item | 주문 / 주문 항목 |
| favorite_movie / favorite_theater | 찜한 영화 / 찜한 영화관 |

## 1-2. 테이블 관계

- theater 1 : N screen 1 : N screening 1 : N seat
- screening N : 1 movie
- seat 1 : 1 reservation N : 1 member
- theater 1 : 1 store
- store N : M menu (menu_stock으로 연결)
- member N : 1 orders 1 : N order_item N : 1 menu
- member N : M movie (favorite_movie), member N : M theater (favorite_theater)

## 1-3. 핵심 설계 포인트

1. seat를 screen이 아닌 screening에 종속시킴 —> 같은 물리적 좌석도 회차마다 예약 상태가 달라야 하므로
2. reservation.seat_id에 UNIQUE 제약 —> 동시 예약 경쟁 상황에서도 하나의 좌석에 하나의 예약만 존재하도록 DB 레벨에서 중복 예약을 차단
3. store.theater_id에 UNIQUE 제약 — 영화관 하나당 매장 하나라는 비즈니스 규칙을 DB 제약으로 강제
4. menu-store를 menu_stock 중간 테이블로 연결 — 같은 메뉴라도 매장마다 재고가 다르므로, 재고를 매장 단위로 독립 관리하기 위해 중간 테이블을 둠
5. order_item에 price, quantity를 별도 저장 — 메뉴를 참조만 하지 않고 주문 시점의 가격·수량을 저장해, 이후 메뉴 가격이 바뀌어도 과거 주문 내역이 변하지 않도록 설계


# 2. 추가 학습 내용 정리

## 2-1. Dirty Checking과 전체 컬럼 UPDATE

Hibernate는 엔티티마다 UPDATE SQL을 캐싱해서 재사용한다. 
이 SQL은 모든 컬럼을 SET 절에 포함하도록 미리 만들어지기 때문에, 
필드 하나만 바뀌어도 Dirty Checking이 감지하면 전체 컬럼이 UPDATE 대상이 된다.

따라서, 

- 컬럼 수가 많고 그중 일부만 자주 바뀌는 엔티티
- 컬럼에 DB 트리거가 걸려 있어, 변경되지 않은 컬럼까지 트리거가 동작하면 안 되는 경우

라면, 다음의 방법으로 효율을 높일 수 있다

### 개선 방법 — `@DynamicUpdate`

```java
@Entity
@DynamicUpdate
public class Member {
    @Id
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
}
```

위 코드처럼 엔티티 클래스에 `@DynamicUpdate`를 붙이면 Hibernate가 고정 SQL 대신, 
실제로 변경된 컬럼만 포함한 UPDATE 문을 매번 동적으로 생성한다.


### 주의할 점

| 상황 | 권장 여부 |
|---|---|
| 컬럼이 많고 일부 필드만 자주 변경 | `@DynamicUpdate` 적용 권장 |
| 컬럼이 적거나 대부분 필드가 함께 변경 | 기본(정적 UPDATE) 유지가 더 나을 수 있음 (동적 SQL 생성 비용 발생) |
| detached 엔티티를 `Session.update()`로 재부착 | `@SelectBeforeUpdate`를 함께 적용해야 `@DynamicUpdate`가 정상 동작 |


---

## 2-2. Flush가 발생하는 시점

Flush는 영속성 컨텍스트의 변경 내용을 실제 DB에 동기화하는 과정이다. 
아래 세 시점 모두 실제로 flush가 발생하는 시점이 맞다.

아래 예시에서 공통으로 쓰이는 tx는 트랜잭션을 다루는 EntityTransaction 타입 변수로,
EntityManager에서 아래와 같이 얻는다.

```java
EntityManager em = emf.createEntityManager();
EntityTransaction tx = em.getTransaction();
```

### ① `em.flush()` 직접 호출

```java
Member member = em.find(Member.class, 1L);
member.setName("변경된 이름");

em.flush();  // 이 시점에 즉시 UPDATE 쿼리 실행 (commit 전이라도)
```

이때 Dirty Checking의 기준이 되는 스냅샷도 flush된 값으로 함께 갱신된다. 
그래야 이후 다시 dirty checking을 할 때 방금 flush한 값을 기준으로 비교할 수 있다.

### ② 트랜잭션 commit 시

```java
tx.begin(); // 트랜잭션 시작
Member member = em.find(Member.class, 1L);
member.setName("변경된 이름");
tx.commit();  // 트랜잭션 커밋. 이 직전에 자동으로 flush 발생
```

### ③ JPQL 쿼리 실행 직전 (FlushMode.AUTO, 기본값)

```java
tx.begin(); // 트랜잭션 시작
Member member = em.find(Member.class, 1L);
member.setName("변경된 이름");

// 변경사항이 아직 DB에 반영되지 않았지만
// 아래 JPQL이 member 테이블을 조회하므로, 실행 직전 자동 flush 발생
List<Member> result = em.createQuery(
    "select m from Member m where m.name = :name", Member.class)
    .setParameter("name", "변경된 이름")
    .getResultList();
```

단, 항상 발생하는 것은 아니고, 실행하려는 쿼리가 아직 flush되지 않은 변경사항과 겹치는 테이블을 조회할 때만 선행 flush가 일어난다. 

아래와 같은 네이티브 SQL은 기본적으로 자동 flush 대상에서 제외된다.
```java
em.createNativeQuery("SELECT * FROM member WHERE name = ?")
  .setParameter(1, "변경된 이름")
  .getResultList();
```

네이티브 SQL은 Hibernate가 직접 파싱하는 JPQL과는 달리 그냥 문자열이라 Hibernate가 해당 SQL이 어떤 테이블을
건드리는지 알 수 없다. 그래서 기본적으로 자동 flush 대상에서 제외된다.
그 결과 네이티브 쿼리 실행 전 변경사항이 아직 DB에 반영되지 않아, stale(최신이 아닌) 데이터를 조회할 위험이 있다.
addSynchronizedEntityClass()로 동기화 대상 엔티티를 명시하거나, 쿼리 전 em.flush()를 직접 호출하여 해결해야 한다.

### FlushMode 정리

| 모드 | 동작 |
|---|---|
| `AUTO` (기본값) | commit 시 + 관련 쿼리 실행 직전에 flush |
| `COMMIT` | commit 시에만 flush |
| `MANUAL` | `flush()`를 명시적으로 호출할 때만 flush (읽기 전용 트랜잭션에 유리) |
| `ALWAYS` | 모든 쿼리 실행 전 매번 flush (공식 문서에서 "거의 항상 불필요"하다고 명시) |


---

## 2-3. 영속성 컨텍스트 · 엔티티 매니저 · 트랜잭션의 관계

결론부터 말하면, 셋이 항상 1:1은 아니다.

### 영속성 컨텍스트 ↔ 엔티티 매니저

Container-managed 환경에서는 영속성 컨텍스트 전파가 일어난다.

```java
// 동일한 JTA 트랜잭션 내에서
@Stateless
public class OrderService {
    @PersistenceContext
    EntityManager em1;   // 서로 다른 엔티티 매니저 인스턴스라도
}

@Stateless
public class PaymentService {
    @PersistenceContext
    EntityManager em2;   // 같은 트랜잭션 안이면 em1과 동일한 영속성 컨텍스트를 공유
}
```

- **Transaction-Scoped(기본값)** + Container-managed 
- → 여러 엔티티 매니저가 하나의 영속성 컨텍스트를 공유할 수 있음 (N:1)
- 
- **Application-managed** 엔티티 매니저 
- → 영속성 컨텍스트를 공유하지 않고 각자 독립적으로 가짐
- 
- **Extended(확장) 스코프** 
- → 특정 엔티티 매니저에 종속되어 생성·종료 시점을 함께함 (1:1에 가까움)

### 엔티티 매니저 ↔ 트랜잭션

```java
// Transaction-Scoped: 트랜잭션이 끝나면 영속성 컨텍스트도 함께 닫힘
tx.begin();
Member m = em.find(Member.class, 1L);
tx.commit();   // 여기서 영속성 컨텍스트 종료

tx.begin();    // 같은 em이라도 새로운 영속성 컨텍스트가 다시 생성됨
Member m2 = em.find(Member.class, 1L);
tx.commit();
```

반면 Extended 영속성 컨텍스트는 여러 트랜잭션에 걸쳐 존재할 수 있다.

```java
@Stateful
public class OrderEditor {
    @PersistenceContext(type = PersistenceContextType.EXTENDED)
    EntityManager em;
    // 이 em(영속성 컨텍스트)은 하나가 여러 트랜잭션에 걸쳐 유지될 수 있음
}
```

## 2-4. Proxy

### 2-4-1. Hibernate Proxy vs Spring AOP Proxy

| 구분 | Hibernate Proxy | Spring AOP Proxy |
|---|---|---|
| 목적 | 지연 로딩 | 횡단 관심사(트랜잭션, 로깅 등) 적용 |
| 대상 | JPA 엔티티 | 스프링 빈 |
| 구현 | 바이트코드 조작(cglib 등)으로 엔티티 상속 프록시 생성 | 인터페이스 구현 시 JDK 동적 프록시, 아니면 CGLIB |

둘 다 실제 객체 대신 동작을 가로채는 대리 객체이지만,
Hibernate Proxy는 데이터 접근 시점 제어가,
Spring AOP Proxy는 메서드 호출 가로채기가 목적이라는 점에서 근본적으로 다르다.

### 2-4-2. 양방향 매핑 + `@OneToOne` + `nullable=true`에서 프록시 문제

```java
@Entity
public class Member {
    @Id
    private Long id;

    @OneToOne(mappedBy = "member", fetch = FetchType.LAZY)
    private MemberProfile profile;   // 비주인 쪽 (nullable=true, 기본값)
}

@Entity
public class MemberProfile {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;           // 주인 쪽 (FK 보유)
}
```
위와 같은 상황에서

```java
Member member = em.find(Member.class, 1L);
```
를 실행하면, FetchType.LAZY로 지정했음에도 member.profile은 실제로 즉시 로딩된다. 

왜일까?

Member(비주인) 쪽에는 profile 관련 컬럼이 테이블에 아예 없다. 
FK는 반대쪽인 MemberProfile 테이블에 있기 때문에,
Member를 조회하는 시점에는 이 회원에게 프로필이 있는지 없는지 Hibernate가 전혀 알 수 없는 것이다.

그런데 프록시는 "대상이 반드시 존재한다"를 전제로 만드는 빈 껍데기이기 때문에,
지금 이 자리에서 null로 둘지 프록시로 채울지 둘 중 하나로 확정해야 한다 (나중에 바꿀 수 없음).

그래서 Hibernate는 이 판단을 위해 MemberProfile 테이블을 조회해
"나를 참조하는 row가 있는지" 먼저 확인하는 SELECT를 추가로 날린다.
근데?? 이 확인 쿼리 자체가 이미 즉시 조회이기 때문에,
결과적으로 FetchType.LAZY를 지정해도 즉시 로딩처럼 동작하는 것이다.

---

## 2-5. Fetch Join + 페이징 문제

### 문제

컬렉션(1:N 관계인 comments)을 fetch join하면서 페이징(`setMaxResults`)을 동시에 걸면 문제가 생긴다.

```java
List<Post> posts = em.createQuery("""
    select p
    from Post p
    left join fetch p.comments
    order by p.createdOn
    """, Post.class)
    .setMaxResults(5)
    .getResultList();
```
분명히 5개만 가져오라고 `setMaxResults(5)`를 지정했지만, 
Hibernate는 이걸 그대로 실행하지 않고 경고를 띄운다.
```
WARN: HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
```
"in memory!"라는 말대로, DB에는 페이징 조건을 아예 전달하지 않고 
전체 데이터를 다 가져온 뒤 자바 메모리 안에서 5개만 잘라낸다는 뜻이다. 
실제 실행되는 SQL을 보면 이게 명확히 드러난다.

```sql
-- 실제 실행되는 SQL — LIMIT/OFFSET이 빠져 있음
SELECT p.id, p.created_on, p.title,
       c.id, c.post_id, c.review
FROM post p
LEFT OUTER JOIN post_comment c ON p.id = c.post_id
ORDER BY p.created_on
```

왜..?

컬렉션(1:N)을 fetch join하면 부모 1개에 자식 row가 여러 개 매핑된 결과셋이 만들어진다. 
이 상태에서 DB 레벨로 단순히 `LIMIT`을 걸면 자식 컬렉션 중간에서 결과가 잘려, 
부모 엔티티가 자식 데이터를 일부만 가진 채 반환될 수 있다. 
Hibernate는 데이터 일관성을 우선시하기 때문에, 
이 경우 SQL 레벨 페이징을 포기하고 전체 결과를 조회한 뒤 메모리에서 페이징을 수행한다. 

그 결과로 페이징 조건과 무관하게 전체 데이터가 매번 조회되는 엄청난 부작용이 생기는 것이다

### 해결 방법

**방법 1 — ID를 먼저 페이징 조회 후, 해당 ID로 fetch join**

```java
// 1단계: ID만 페이징 조회 (SQL 레벨 LIMIT 정상 적용)
List<Long> ids = em.createQuery("""
    select p.id
    from Post p
    order by p.createdOn
    """, Long.class)
    .setFirstResult(0)
    .setMaxResults(5)
    .getResultList();

// 2단계: 조회된 ID로 fetch join (컬렉션 전체를 가져와도 페이징 대상이 이미 5개로 고정됨)
List<Post> posts = em.createQuery("""
    select distinct p
    from Post p
    left join fetch p.comments
    where p.id in :ids
    order by p.createdOn
    """, Post.class)
    .setParameter("ids", ids)
    .getResultList();
```

**방법 2 — `@BatchSize`로 N+1 완화 (fetch join 대신 지연 로딩 유지)**

```java
@Entity
public class Post {
    @Id
    private Long id;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private List<Comment> comments = new ArrayList<>();
}

// 부모는 정상적으로 SQL 레벨 페이징 적용
List<Post> posts = em.createQuery(
    "select p from Post p order by p.createdOn", Post.class)
    .setFirstResult(0)
    .setMaxResults(5)
    .getResultList();
// comments 접근 시 BatchSize 단위로 IN 절 묶어서 조회 (N+1 완화)
```

**방법 3 — 조기 감지를 위한 설정**

```properties
# 경고 로그 대신 예외를 발생시켜 실수를 조기에 발견
hibernate.query.fail_on_pagination_over_collection_fetch=true
```

#3. 3주차 README

## 3-1. JWT를 활용한 인증 흐름 정리하기

###3-1-1. JWT의 Header, Payload, Signature는 각각 어떤 역할을 하나요?

- Header: 토큰의 메타데이터(암호화 알고리즘과 토큰의 타입)를 담는다.
- Payload: 실제 전달하려는 Claim 데이터를 담는다. 유저 식별자(PK), 토큰 발급 및 만료 시간, 권한 등의 실질적인 정보를 포함한다. Base64로 인코딩될 뿐 암호화되지 않으므로 민감한 정보를 포함해서는 안 된다!
- Signature: 토큰의 위변조 여부를 검증한다. 서버가 가진 Secret Key를 이용하여 Header와 Payload를 암호화해 생성한다. Payload가 변조될 경우 서명이 일치하지 않아 서버에서 유효하지 않은 토큰으로 판단하고 차단한다.

#### 3-1-2. Access Token과 Refresh Token은 무엇이 다른가요?

- Access Token: 서버의 API 리소스에 접근할 때마다 헤더에 포함하여 전송하는 실질적인 자격 증명 수단이다. 탈취 위험을 최소화하기 위해 유효 기간을 짧게 설정한다.
- Refresh Token: 유효 기간이 길며, 만료된 Access Token을 새로 발급받기 위한 용도로만 사용한다. 탈취 시의 위험을 줄이기 위해 데이터베이스나 Redis에 저장하여 상태를 관리하며(RTR
  등), 일반적인 API 리소스 접근에는 사용할 수 없다!

#### 3-1-3. 쿠키와 세션, JWT는 각각 어떤 역할을 하나요?

- 쿠키: 브라우저(클라이언트) 단에 데이터를 저장하는 매체이다. 세션 ID를 보관할 수도 있고,
  JWT를 보관할 수도 있으며, 매 HTTP 요청 시 서버로 자동 전송된다.
- 세션: 유저의 상태를 서버의 메모리에 저장하는 방식이다. 브라우저에는 고유한 세션 ID만
  쿠키로 발급한다. 보안성은 높지만, 동시 접속자가 많아지면 서버 메모리 부하가 증가하고 다중
  서버(Scale-out 시) 환경에서 관리가 까다롭다.
- JWT: 유저의 정보를 암호화 및 서명하여 토큰 자체에 저장하는 Stateless한 인증 방식. 서버는 메모리에 상태를 저장하지 않으므로 서명 검증만으로 인증이 가능하여 서버 확장에 매우 유리하다.

#### 3-1-4. CGV 프로젝트의 Access Token에는 어떤 Claim이 필요한가요?

저는 현재 아래 5가지 클레임으로 토큰을 구성해봤습니다.

- iss (Issuer): 토큰 발급자. 현재 "http://localhost:8080"으로 고정되어 있으며, 파싱 시 requireIssuer()를 통해 발급자가 일치하는지 검증한다.
- sub (Subject): memberId를 담는다. 필터에서 이 값을 추출해 데이터베이스에서 회원 정보를 조회하는 데 사용한다.
- type: 해당 토큰이 Access Token인지 Refresh Token인지 구분하는 사용자 정의 클레임. Refresh Token을 헤더에 넣어 일반 API 리소스에 접근하는 것을 원천 차단하기 위해 추가하였음.
- iat (Issued At): 토큰이 발급된 시간 정보.
- exp (Expiration Time): 토큰의 만료 시간.

매번 memberId를 통해 DB를 조회하여 권한 확인 하는 과정을 개선하기 위해, Role 클레임을 넣어 DB 조회 없이 권한을 확인하게 한다거나, 블랙리스트 기능을 위한 jti(JWT ID) 등을 추가해도 좋을 것 같네요.

#### 3-1-5. JWT 검증 결과가 Authentication과 SecurityContext로 어떻게 연결되나요?

1. JwtAuthenticationFilter에서 토큰의 서명을 검증하고 memberId를 추출한다.
2. 추출한 정보로 CustomUserDetails 객체를 생성한 뒤, 이를 UsernamePasswordAuthenticationToken으로 래핑한다.
3. 생성된 Authentication 인증 객체를 스프링 시큐리티의 전역 저장소인 SecurityContextHolder 내의 SecurityContext에 저장한다.
4. 이후 컨트롤러 단에서 @AuthenticationPrincipal 어노테이션을 통해 SecurityContext에 저장된 유저 정보를 꺼내어 사용할 수 있다.

#### 3-1-6. 인증과 인가는 어떻게 다르며, 401과 403은 각각 언제 반환하나요?

- 인증 (Authentication)
    - 클라이언트가 누구인지 신원을 확인하는 과정이다.
    - 401 Unauthorized : 비로그인 상태, 토큰 미제공, 토큰 만료, 토큰 위변조 등 유효한 인증 자격 증명이 없을 때 반환한다.
- 인가 (Authorization)
    - 개념: 인증된 사용자가 특정 리소스에 접근할 수 있는 권한이 있는지 확인하는 과정이다.
    - 403 Forbidden : 정상적으로 로그인(인증)은 했으나, 일반 유저가 관리자 전용 기능에 접근하려는 등 요구되는 권한(Role)을 충족하지 못했을 때 반환한다.

#### (기타) CSRF를 비활성화한 이유

CSRF 공격은 브라우저가 요청마다 쿠키를 자동으로 첨부하는 특징을 악용하는 공격이다. 하지만 내 서버는 세션과 인증 쿠키를 사용하지 않는 Stateless 서버이며, 매 요청 시 클라이언트가 명시적으로 헤더에 토큰을 담아 보내야만 인증이 가능하므로 CSRF 공격으로부터 안전하다.

#### (기타) 공개 경로에서는 인가 거부가 일어나지 않을 수도 있다?

현재 JWT 검증 로직은 예외 발생 시 필터 계층에서 즉각적인 401 에러 응답을 반환하지 않고, 예외 원인을 `request attribute` 에 저장한 뒤 `AuthenticationEntryPoint` 로 위임하여 인가 단계에서 처리되도록 설계했다. 이러면 전체 영화 조회와 같이 `permitAll()` 로 설정된 공개 경로에 접근할 때 클라이언트가 만료되거나 잘못된 토큰을 전송하더라도 예외를 발생시키지 않고 인증 정보가 빈 익명 사용자로서 정상적인 조회를 보장할 수 있다.

# 4주차 README

## 1. 객체지향 코드 리팩토링

기존 코드는 엔티티가 데이터만 담고 있고, 서비스 계층에서 값을 꺼내 직접 계산하거나 상태를 바꾸는 방식이 많았음.
이번 리팩토링에서는 객체가 스스로의 데이터를 다루도록 역할을 넘기고 도메인의 책임을 강화하는 데 집중함.

### 1-1. 주문 도메인 (Order, OrderItem)

- `OrderItem` 캡슐화와 계산 책임 부여
  - 기존에는 `OrderItem`이 가격과 수량만 필드로 가지고 있고, 소계 금액(`가격 * 수량`) 계산 로직이 없어 `OrderService`가 getter로 값을 꺼내 계산했음.
  - 이를 `OrderItem` 내부의 `getSubtotal()` 메서드로 옮겨 객체가 스스로 금액을 계산하도록 변경함.
  - 생성자를 `protected`로 닫고 정적 팩터리 메서드(`create`)를 도입하여, 메뉴나 수량에 잘못된 값이 들어오지 않도록 생성 시점에 유효성 검증을 거치도록 함.

- `Order`와 영속성 전이 적용
  - 총 결제 금액 계산(`calculateTotalPrice`)과 주문 항목 추가 로직을 `Order` 엔티티 내부로 응집시킴.
  - `Order`와 `OrderItem` 사이에 `CascadeType.PERSIST`를 적용하여 `Order`를 저장할 때 `OrderItem`들도 함께 영속화되도록 개선함. 덕분에 서비스 계층에서 `OrderItemRepository`를 직접 주입받아 개별 저장할 필요가 없어짐.

### 1-2. 예매 및 좌석 도메인 (Reservation, Seat)

- `Seat` 도메인의 상태 제어
  - 기존에는 외부에서 좌석의 예약 여부를 직접 변경할 수 있어 상태가 깨질 위험이 있었음.
  - 이를 `reserveSeat()`, `cancelSeat()` 메서드로 제한하고, 이미 예약된 좌석을 다시 예약하려 하면 엔티티 내부에서 예외를 던지도록 캡슐화함.

- `Reservation` 검증 로직 이전
  - 예매 생성 시 좌석/회원 유효성을 확인하고 취소 시 좌석 상태를 되돌리는 흐름을 도메인 내부(`Reservation.create`, `cancel`)로 정리하여 서비스 코드를 간결하게 유지함.


---

## 2. 동시성 문제 분석과 해결

### 2-1. 동시성 문제란?

- 두 개 이상의 스레드나 프로세스가 동일한 공유 자원에 동시에 접근해 상태를 변경하려 할 때, 실행 순서나 타이밍에 따라 데이터 정합성이 깨지는 현상임.
- Spring Boot 웹 애플리케이션은 기본적으로 Thread-Per-Request 방식으로 동작함. HTTP 요청이 들어오면 Tomcat 스레드 풀에서 스레드를 할당해 컨트롤러와 서비스를 실행하므로, 여러 요청이 동시에 같은 DB row를 읽고 수정하면 경합이 발생함.

### 2-2. 동시성 해결 방법 비교

#### ① JVM 레벨 동시성 제어 (`synchronized`, `ReentrantLock`)
- 동작 원리: Java 객체 모니터 락을 이용해 한 번에 하나의 스레드만 임계 영역에 진입하도록 제어함.
- 장점:
  - 별도 인프라 없이 Java 언어 기능만으로 간단히 적용 가능함.
  - 단일 JVM 메모리 내부에서 동작하므로 오버헤드가 적음.
- 단점:
  - Spring `@Transactional` AOP 프록시와의 시점 차이 문제:
    - 실행 순서: `1. 프록시 트랜잭션 시작` -> `2. synchronized 진입` -> `3. 비즈니스 로직 수행` -> `4. synchronized 탈출(락 해제)` -> `5. 프록시 트랜잭션 커밋`.
    - 4번에서 락이 풀린 직후 5번 커밋이 끝나기 전에 다른 스레드가 진입해 DB를 조회하면, 아직 커밋 전인 과거 데이터를 읽게 되어 Lost Update가 그대로 발생할 수 있음.
  - 서버가 2대 이상으로 늘어나면(Scale-out) JVM 메모리가 분리되어 서버 간 락이 공유되지 않음.
- 적절한 상황: 단일 서버 인스턴스 내부의 인메모리 캐시나 정적 자원 동기화.

#### ② DB 비관적 락 (Pessimistic Lock - `PESSIMISTIC_WRITE`)
- 동작 원리: 데이터를 조회할 때 DB의 배타 락(`SELECT ... FOR UPDATE`)을 걸어, 현재 트랜잭션이 끝날 때까지 다른 트랜잭션이 해당 row를 수정하지 못하도록 대기시킴.
- 장점:
  - 충돌이 잦을 때 롤백·재시도 없이 락 획득 순서대로 대기 후 처리하므로 데이터 정합성을 확실히 보장함.
  - DB 레벨에서 락을 관리하므로 다중 서버 환경에서도 동일하게 동작함.
- 단점:
  - 동시 요청들이 락 해제를 기다려야 하므로 처리량이 낮아질 수 있음.
  - 여러 row를 서로 다른 순서로 잠그면 데드락이 발생할 수 있어 락 획득 순서에 유의해야 함.
- 적절한 상황: 동시 충돌 빈도가 높고 재고·좌석처럼 정합성이 중요한 비즈니스 로직.

#### ③ DB 낙관적 락 (Optimistic Lock - `@Version`)
- 동작 원리: 물리적 DB 락을 걸지 않고 `version` 컬럼을 둠. 수정 시 `UPDATE ... WHERE id = ? AND version = ?`를 실행해 버전이 일치할 때만 반영하고, 이미 다른 트랜잭션이 수정했다면 `OptimisticLockingFailureException`을 발생시킴.
- 장점:
  - DB 락을 잡고 대기하지 않으므로 충돌이 적을 때 읽기/쓰기 성능이 좋음.
  - 락 대기가 없어 데드락이 발생하지 않음.
- 단점:
  - 충돌 발생 시 트랜잭션이 롤백되므로, 재시도(Retry) 로직을 직접 구현해야 함.
  - 충돌이 잦은 구간에서는 반복되는 롤백과 재시도로 인해 오히려 DB 부하가 커짐.
- 적절한 상황: 동시 수정 충돌 확률이 낮고 읽기가 압도적으로 많은 도메인.

#### ④ DB 네임드 락 (Named Lock - MySQL `GET_LOCK()`)
- 동작 원리: 테이블이나 레코드가 아니라 임의의 문자열 키를 기준으로 MySQL 서버에서 `GET_LOCK()`, `RELEASE_LOCK()`을 호출해 잠금을 획득·반납함.
- 장점:
  - 스키마 변경 없이 비즈니스 키 단위로 락을 걸 수 있음.
  - 아직 DB에 존재하지 않는 레코드에 대한 동시 작업도 제어 가능함.
- 단점:
  - 트랜잭션이 끝나도 자동 해제되지 않고 DB 세션(커넥션)에 귀속되므로 `try-finally`로 직접 해제해야 함.
  - 락 점유용 커넥션과 비즈니스 트랜잭션용 커넥션을 분리해야 해서 커넥션 풀 관리가 까다로움.
- 적절한 상황: Redis 같은 외부 인프라 없이 특정 비즈니스 키 단위로 잠금이 필요한 배치/스케줄러 작업.

#### ⑤ Redis 분산 락 (Lettuce vs Redisson)
- 동작 원리: 여러 서버가 중앙 Redis를 통해 락을 획득/해제함.
  - Lettuce: `SETNX`로 락 획득을 반복 시도하는 스핀 락 방식.
  - Redisson: Pub/Sub 기반으로 락 해제 신호를 받아 시도하며, Watchdog으로 만료 시간을 연장함.
- 장점:
  - 인메모리 기반이라 빠르고 DB 커넥션을 점유하지 않음.
  - 다중 서버 및 MSA 환경에서 공통 락으로 사용하기 좋음.
- 단점:
  - Redis 인프라 구축·운영 비용과 추가 의존성이 필요함.
  - Redis 장애 시 대응(SPOF) 구성을 신경 써야 함.
- 적절한 상황: 대규모 트래픽 환경이나 여러 서버가 공유 자원을 제어해야 하는 분산 환경.

---

### 2-3. 우리 서비스에서 발견한 동시성 문제와 해결 방법

#### 지점 1: 매점 상품 주문 시 재고 차감 (Lost Update)
- 발생 원인:
  1. 재고가 10개 남은 팝콘에 대해 고객 A, B가 동시에 주문 요청을 보냄.
  2. 두 트랜잭션이 거의 동시에 `MenuStock`을 조회해 둘 다 재고를 10개로 읽음.
  3. 각자 1개씩 차감해 재고를 9개로 수정한 뒤 커밋하면, 총 2개가 팔렸는데 DB 재고는 9개로 남는 갱신 유실(Lost Update)이 발생함.
- 선택한 해결 방법: **DB 비관적 락 (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)**
- 선택 근거:
  - 한정된 재고를 여러 사용자가 동시에 주문하는 다대일 경합 구조라 충돌 가능성이 높음.
  - 낙관적 락을 쓰면 재고가 충분한데도 동시 요청이라는 이유로 실패하고 계속 재시도를 돌아야 해서 비효율적임.
  - 단일 DB 환경에서 Redis까지 도입하는 것은 불필요하다고 판단하여, 조회 시점에 `SELECT ... FOR UPDATE`로 순차 차감을 보장하는 비관적 락을 적용함.

#### 지점 2: 영화 좌석 예매 (Double Booking)
- 발생 원인:
  1. 아직 예약되지 않은 좌석(`isReserved = false`)에 대해 두 사용자가 동시에 예매를 요청함.
  2. 두 트랜잭션 모두 좌석을 비어 있는 상태로 조회하고 `seat.reserveSeat()` 검증을 통과함.
  3. 둘 다 `Reservation` 저장을 시도하면, `reservation.seat_id`의 UNIQUE 제약조건 때문에 늦게 커밋되는 쪽에서 도메인 예외(`ALREADY_RESERVED`, 400) 대신 `DataIntegrityViolationException`(500 에러)이 발생함.
- 선택한 해결 방법: **좌석 조회 시 DB 비관적 락 적용**
- 선택 근거:
  - DB 유니크 제약만으로도 중복 저장은 막히지만, 뒤늦은 요청에 500 에러 대신 명확한 비즈니스 예외(`이미 예약된 좌석입니다.`)를 내려주기 위해 좌석 조회 쿼리(`findByScreeningIdAndSeatNumber`)에 비관적 락을 걸어 요청을 직렬화함.

#### 지점 3: 영화 및 영화관 찜하기 (Check-Then-Act)
- 발생 원인:
  - `existsBy...`로 중복 여부를 확인한 뒤 `save()`를 호출하는 구조라, 사용자가 찜 버튼을 빠르게 연타하면 두 요청이 동시에 `existsBy`를 통과해 중복 `INSERT`를 시도할 수 있음.
- 선택한 해결 방법: **DB 복합 유니크 제약조건 (`@UniqueConstraint`)**
- 선택 근거:
  - 여러 사용자가 경쟁하는 자원이 아니라 단일 사용자의 중복 클릭 문제이므로, 매번 DB 락을 거는 것보다 테이블의 `(movie_id, member_id)`, `(theater_id, member_id)` 복합 유니크 제약으로 중복 생성을 막는 것이 단순하고 효율적임.

#### 지점 4: Refresh Token Rotation(RTR) 재발급 동시 요청
- 발생 원인:
  - 프론트엔드에서 여러 API 호출이 동시에 만료되어 `/auth/reissue`가 거의 동시에 2번 이상 호출될 때, 두 요청이 같은 Refresh Token을 가지고 진입함.
- 발생하는 문제:
  - 락이 없으면 중복 토큰이 발급될 수 있고, 반대로 비관적 락만 걸어 순차 처리하면 첫 번째 요청이 토큰을 `isUsed = true`로 바꾼 직후 두 번째 요청이 진입해 **정상 사용자를 토큰 탈취(`AUTH_HIJACK_DETECTED`)로 오인하여 모든 세션을 강제 삭제하는 문제**가 발생함.
- 선택한 해결 방법: **비관적 락 + 유예 기간(Grace Period 10초) 멱등 반환**
- 선택 근거:
  - `RefreshTokenRepository.findByToken`에 비관적 락을 걸어 동시 접근 순서를 고정함.
  - 이미 사용된 토큰이라도 사용 시각(`usedAt`)으로부터 10초 이내에 들어온 요청이라면 탈취로 처리하지 않고, 직전에 새로 발급해 둔 Access/Refresh Token을 그대로 다시 반환하도록 구현함. 10초가 지난 뒤의 재사용 요청만 실제 탈취로 판단해 세션을 삭제함.

---

### 2-4. 동시성 문제 재현 및 비관적 락 적용 전/후 결과 비교

#### 1) 멀티스레드 재현 테스트 구성 (`OrderConcurrencyTest`, `ReservationConcurrencyTest`, `AuthReissueConcurrencyTest`)
- `ExecutorService`와 3개의 `CountDownLatch`(`readyLatch`, `startLatch`, `doneLatch`)를 사용해 동시 요청 환경을 구성함.
  1. `readyLatch`: 모든 작업 스레드가 생성되어 대기 상태에 들어갈 때까지 메인 스레드가 기다림.
  2. `startLatch`: 모든 스레드가 준비되면 `startLatch.countDown()`으로 동시에 요청을 출발시킴.
  3. `doneLatch`: 전체 스레드의 작업(커밋/롤백)이 끝난 뒤 최종 DB 상태를 검증함.

#### 2) 락 미적용 시 재현 결과 (As-Is)
- 조건: 초기 재고 100개에 대해 100개 스레드가 동시에 1개씩 주문 요청.
- 결과:
  - 100건의 주문 요청은 모두 성공 처리되었으나, 최종 남은 재고는 0개가 아닌 **82개**가 남음.
- 원인: 여러 스레드가 커밋 전의 동일한 재고 값을 읽고 덮어쓰면서 82건의 차감이 유실됨(Lost Update).

#### 3) 비관적 락 적용 후 검증 결과 (To-Be)
- 검증 1 — 재고 100개에 100명 동시 주문 (`OrderConcurrencyTest`):
  - 성공 100건, 실패 0건, **최종 남은 재고 0개**로 정확히 차감됨.
- 검증 2 — 재고 10개에 50명 동시 주문 (`OrderConcurrencyTest`):
  - 선착순 **10건만 성공**하고 나머지 **40건은 재고 부족 예외(`OUT_OF_STOCK`)로 실패**, 최종 남은 재고는 음수로 내려가지 않고 **0개**로 유지됨.
- 검증 3 — 단일 좌석에 50명 동시 예매 (`ReservationConcurrencyTest`):
  - **1건만 성공**하고 나머지 **49건은 이미 예약된 좌석 예외(`ALREADY_RESERVED`)로 실패**, 생성된 예매 데이터는 정확히 **1건**으로 유지됨.
- 검증 4 — Refresh Token 10개 스레드 동시 재발급 (`AuthReissueConcurrencyTest`):
  - 탈취 오탐지(`AUTH_HIJACK_DETECTED`) 없이 10건 모두 성공하며, 10개 스레드 모두 동일한 신규 토큰 세트를 반환받음.

---

## 3. 토스페이먼츠 결제 시스템 연동

매점 주문(`Order`)에 대해 토스페이먼츠 테스트 API를 연동하여 결제 준비 -> 승인 -> 조회 -> 취소 흐름을 구현함.

### 3-1. 주문 및 결제 흐름과 상태 머신

- 전체 결제 흐름
  1. 주문 생성(`POST /api/orders`) 후 결제 준비(`POST /api/payments/prepare`)를 호출하면 토스 연동용 `orderId` 발급과 함께 `READY` 상태의 `Payment`가 생성됨.
  2. 클라이언트가 토스 결제창에서 인증을 마친 뒤 `paymentKey`, `orderId`, `amount`로 결제 승인(`POST /api/payments/confirm`)을 요청함.
  3. 서버에서 주문 및 금액을 검증한 뒤 토스페이먼츠 승인 API(`POST /v1/payments/confirm`)를 호출함.
  4. 승인 완료 시 `Payment` 상태를 `DONE`으로 변경하고 결제 수단과 승인 시각을 저장함.

- 결제 상태(`PaymentStatus`)
  - `READY`: 결제 준비 완료 및 인증 대기 상태
  - `DONE`: 결제 승인 완료 상태
  - `PARTIAL_CANCELED`: 일부 금액/수량만 취소되어 잔액이 남아있는 상태
  - `CANCELED`: 전액 취소 완료 상태
  - `ABORTED`: 클라이언트 오류(4xx) 또는 자동 보상 취소로 인해 결제가 중단된 상태
  - `FAILED`: PG 서버 오류(5xx)나 타임아웃으로 승인이 실패한 상태
  - `MANUAL_RECOVERY_REQUIRED`: 승인 후 내부 오류로 자동 보상 취소를 시도했으나 이마저 실패해 수동 확인이 필요한 상태

### 3-2. 결제 승인 전 서버 검증

- 클라이언트 요청값을 그대로 믿으면 안 되는 이유:
  - 브라우저 개발자 도구나 프록시 툴로 결제 요청 파라미터(`amount`, `orderId` 등)를 임의로 변조해 보낼 수 있음. 예를 들어 15,000원짜리 주문을 생성해 두고 100원만 결제하도록 요청 값을 바꿀 위험이 있음.

- 서버에서 수행한 검증 (`PaymentService.confirmPayment`):
  1. `orderId`에 해당하는 `Payment` 데이터가 우리 DB에 실제로 존재하는지 확인함.
  2. 현재 결제 상태를 확인하여, 이미 `DONE` 상태라면 파라미터(금액, `paymentKey`) 일치 여부 확인 후 기존 결과를 멱등 반환하고, `READY` 상태가 아니라면 승인 요청을 차단함.
  3. DB에 저장된 최초 결제 금액(`payment.getTotalAmount()`)과 클라이언트가 보낸 요청 금액(`request.amount()`)이 일치하는지 비교하여 다르면 `PAYMENT_AMOUNT_MISMATCH` 예외로 차단함.
  4. 토스 승인 응답을 받은 뒤에도 응답 본문의 필수 필드(`paymentKey`, `orderId`, `totalAmount`)가 누락되거나 요청 값과 다른지 한 번 더 검증(`validateIntegrity`)함.

### 3-3. 결제 조회와 역할 분담

- 결제 내역을 조회할 때마다 매번 토스 API를 호출하면 외부 네트워크 비용이 들고, 토스 장애 시 우리 서비스의 조회 기능까지 영향을 받음.
- 따라서 승인 시점에 `paymentKey`, `orderId`, 결제 수단(`method`), 승인 일시(`approvedAt`), 원금(`totalAmount`), 취소 가능 잔액(`balanceAmount`)과 취소 이력(`PaymentCancelHistory`)을 우리 DB에 저장해 두고 자체 DB에서 조회하도록 구현함.
- PG사는 결제 인증·승인·환불 처리를 담당하고, 우리 서버는 주문/품목과 연계된 결제 상태 및 취소 내역 관리를 담당함.

### 3-4. 전체 취소 및 부분 취소와 잔액 관리

- 잔액 및 상태 관리:
  - `Payment` 엔티티에 최초 결제 금액(`totalAmount`)과 별개로 남은 취소 가능 잔액(`balanceAmount`)을 둠.
  - 취소 시 `balanceAmount`에서 취소 금액을 차감하며, 잔액이 0원이 되면 `CANCELED`, 잔액이 남으면 `PARTIAL_CANCELED`로 상태를 변경함.

- 품목(`OrderItem`) 단위 부분 취소와 금액 기반 취소 지원:
  - 취소 요청에 `orderItemId`와 `cancelQuantity`가 전달되면 해당 주문 품목의 취소 가능 수량(`quantity - canceledQuantity`)을 검증한 뒤 품목 단가 기준으로 환불 금액을 계산해 부분 취소하고, 취소된 수량만큼 매점 재고(`MenuStock`)도 함께 복구함.
  - `orderItemId` 없이 취소 요청 시 금액 기준 취소 또는 남은 잔액 전체 취소를 수행하며, 전액 취소 시 남은 모든 품목의 수량 취소 및 재고 복구도 함께 처리함.

- 취소 이력 기록 및 예외 방어:
  - 취소할 때마다 `PaymentCancelHistory`에 취소 요청 키(`cancelRequestId`), 대상 품목, 취소 수량, 취소 금액, 사유, 일시를 저장함.
  - 이미 전액 취소된 결제를 다시 취소하거나 남은 잔액(또는 남은 품목 수량)보다 큰 값을 취소하려는 경우 도메인 검증에서 예외로 차단함.


---

## 4. 중복 결제와 멱등성

### 4-1. 동시성 제어와 멱등성의 차이

- 동시성 제어: 여러 요청이 동시에 같은 자원에 접근할 때 실행 순서를 제어해 데이터가 꼬이지 않게 막는 것임.
- 멱등성(Idempotency): 동일한 요청이 네트워크 재시도나 중복 클릭 등으로 여러 번 들어오더라도, 처음 한 번 처리했을 때와 같은 상태와 응답을 유지하는 성질임.
- 동시성 제어만 있으면 시간차를 두고 들어온 중복 결제 요청이 두 번 처리될 수 있고, 멱등성만 있고 동시성 제어가 없으면 동시에 들어온 두 요청이 모두 초기 상태 검증을 통과할 수 있어 둘을 함께 고려해야 함.

### 4-2. 결제 승인 및 취소에서 멱등성을 적용한 방법

- 결제 승인 멱등성:
  - 이미 `DONE` 상태인 주문에 대해 동일한 `orderId`, `paymentKey`, `amount`로 재요청이 들어오면 토스 승인 API를 다시 호출하지 않고 저장된 결제 정보를 그대로 반환함(만약 금액이나 키가 다르면 위변조 요청으로 판단해 예외 발생).
  - 토스 승인 API 호출 시에도 `Idempotency-Key` 헤더에 `orderId`를 담아 전송함.

- 결제 취소 멱등성:
  - 취소 요청 시 고유한 `cancelRequestId`를 받아, 이미 `PaymentCancelHistory`에 존재하는 `cancelRequestId`라면 토스 취소 API를 다시 호출하지 않고 현재 결제 상태를 그대로 반환함.
  - 토스 취소 API 호출 시에도 `Idempotency-Key` 헤더에 `cancelRequestId`를 전달해 PG사 측 중복 환불을 방지함.


---

## 5. 외부 API 통신 고민과 예외 처리

### 5-1. 외부 API 통신 클라이언트 비교

#### 1) `RestClient` (Spring 6.1+)
- 특징: `RestTemplate`의 동기 블로킹 기반 구조를 유지하면서 `WebClient`처럼 메서드 체이닝(Fluent API) 방식을 제공하는 동기 HTTP 클라이언트임.
- 장점:
  - `spring-web`에 기본 포함되어 있어 별도 의존성 추가가 필요 없음.
  - `.onStatus()`로 4xx/5xx 에러 응답을 직관적으로 분기 처리할 수 있음.
  - Spring MVC의 서블릿 스레드 모델과 자연스럽게 맞물림.
- 단점: 동기 블로킹 방식이므로 대규모 비동기 논블로킹 스트리밍 처리에는 맞지 않음.

#### 2) `RestTemplate`
- 특징: 오랫동안 사용된 템플릿 기반 동기 HTTP 클라이언트임.
- 장점: 레퍼런스가 많고 익숙함.
- 단점: 헤더·요청 조립 코드가 번거롭고 예외 처리 설정이 복잡하며, Spring 공식 문서에서도 신규 코드는 `RestClient` 사용을 권장함.

#### 3) `WebClient` (Spring WebFlux)
- 특징: Netty와 Reactor 기반의 비동기 논블로킹 클라이언트임.
- 장점: 적은 스레드로 많은 외부 I/O를 비동기 병렬 처리할 때 유리함.
- 단점: `spring-boot-starter-webflux` 의존성을 추가해야 하고, 일반 MVC 환경에서 결국 `.block()`으로 동기 대기하게 되면 도입 이점이 줄어듦.

#### 4) `OpenFeign` (Spring Cloud)
- 특징: 인터페이스와 어노테이션 선언만으로 HTTP 클라이언트를 생성해 주는 선언형 클라이언트임.
- 장점: 호출 코드가 간결하고 비즈니스 로직과 분리하기 좋음.
- 단점: Spring Cloud 의존성을 추가해야 하고 Spring Boot 버전에 맞는 호환성 관리가 필요함.

### 5-2. RestClient를 선택한 이유

- 현재 프로젝트는 Spring MVC 기반이고 토스페이먼츠 API(승인, 조회, 취소) 3가지만 동기적으로 호출하면 되므로, 별도 외부 의존성 없이 `spring-web`만으로 사용할 수 있는 `RestClient`가 가장 적합하다고 판단함.
- `.onStatus(HttpStatusCode::isError, ...)`를 통해 토스 에러 응답 JSON(`code`, `message`)을 파싱하여 도메인 예외(`TossPaymentException`)로 변환하기 깔끔했음.

### 5-3. 외부 API 예외 처리 전략

#### 1) 타임아웃 설정 (`RestClientConfig`)
- 외부 PG사 장애 시 응답을 무한정 기다리면 Tomcat 스레드가 고갈될 수 있으므로 `SimpleClientHttpRequestFactory`에 타임아웃을 설정함.
  - Connect Timeout: 5초
  - Read Timeout: 5초
- 타임아웃이나 네트워크 연결 실패(`ResourceAccessException`) 발생 시 `TossPaymentException.timeout(...)`으로 변환해 처리함.

#### 2) 시나리오 1 — 토스 API 호출 자체가 실패한 경우
- HTTP 상태 코드에 따라 결제 상태를 분리해 기록함 (`PaymentRecoveryService`, `Propagation.REQUIRES_NEW`):
  - `4xx Client Error`: 잘못된 요청이나 한도 초과 등이므로 `Payment` 상태를 `ABORTED`로 기록함.
  - `5xx Server Error` 및 타임아웃: PG사 서버 오류나 통신 지연이므로 `Payment` 상태를 `FAILED`로 기록함.
- 메인 트랜잭션이 예외로 롤백되더라도 실패/중단 상태 기록은 남도록 `REQUIRES_NEW` 별도 트랜잭션으로 분리함.

#### 3) 시나리오 2 — 토스 승인은 성공했으나 우리 서버 후속 처리 중 오류가 발생한 경우
- 토스에서는 돈이 빠져나갔는데 우리 서버에서 응답 검증 실패나 완료 처리 중 예외가 발생하면, 사용자만 결제 금액이 차감되는 불일치가 생김.
- 이를 방어하기 위해 후속 처리에서 예외가 발생하면 `executeCompensatingCancel()`을 호출하여 방금 승인된 `paymentKey`로 토스 전액 취소 API를 자동 호출(보상 취소)하고 상태를 `ABORTED`로 기록함.
- 만약 네트워크 문제로 이 자동 보상 취소 호출마저 실패한다면, `Payment` 상태를 `MANUAL_RECOVERY_REQUIRED`로 기록하고 관리자 확인이 필요하다는 예외(`PAYMENT_COMPENSATION_FAILED`)를 반환하도록 구현함.

#### 4) 시나리오 3 — 토스 API가 예상치 못한 응답 포맷을 반환한 경우
- 토스 API에 신규 필드가 추가되더라도 파싱 에러가 나지 않도록 `ObjectMapper`에 `FAIL_ON_UNKNOWN_PROPERTIES = false`를 설정함.
- 반대로 필수 필드(`paymentKey`, `orderId`, `totalAmount`)가 누락되었거나 요청한 주문번호/금액과 다르면 `TossPaymentResponse.validateIntegrity()`에서 `PAYMENT_INVALID_RESPONSE` 예외를 던지고 보상 취소를 수행하도록 방어함.


---

## 6. Webhook 알아보기

### 6-1. Webhook이란?
- 특정 시스템에서 이벤트가 발생했을 때, 미리 등록해 둔 서버의 URL로 HTTP POST 요청을 보내 이벤트 발생 사실을 알려주는 역방향 API(Push 방식)임.

### 6-2. Polling과 비교했을 때 Webhook을 쓰는 이유
- Polling은 우리 서버가 주기적으로 PG사에 상태를 물어봐야 해서 불필요한 요청이 많이 발생하고 확인 주기에 따른 지연이 생김.
- Webhook은 결제 상태가 실제로 바뀔 때만 토스 서버가 우리 서버로 알려주므로 불필요한 호출 없이 상태 변경을 반영할 수 있음.

### 6-3. 결제 시스템에서 Webhook이 필요한 상황
1. 가상계좌 등 비동기 결제 수단 처리:
   - 가상계좌는 사용자가 결제창을 닫고 나중에 입금하므로, 입금 완료 시점에 토스가 보내주는 Webhook을 받아야 주문을 확정할 수 있음.
2. 클라이언트 이탈로 인한 승인 요청 누락 복구:
   - 사용자가 토스 결제창 인증을 마친 직후 브라우저가 꺼지거나 네트워크가 끊겨 우리 서버로 `/confirm` 요청을 보내지 못했을 때, Webhook을 통해 결제 상태를 동기화하거나 후속 처리를 할 수 있음.
3. PG사 관리자 콘솔에서 직접 취소한 경우:
   - 운영자가 토스 상점 관리자 페이지에서 직접 환불 처리했을 때 우리 DB의 결제/주문 상태도 함께 취소로 맞추기 위해 필요함.

### 6-4. 현재 서비스에 Webhook을 적용한다면 맡게 될 역할과 고려할 점
1. 서명 검증 (보안):
   - 외부에 열린 엔드포인트이므로 가짜 웹훅 요청을 막기 위해 토스 헤더의 서명 값을 검증해야 함.
2. 멱등성 및 순서 역전 처리:
   - 토스는 응답이 늦으면 동일한 웹훅을 재전송(At-Least-Once)하므로, 이미 `DONE`이나 `CANCELED`로 반영된 건이면 중복 처리하지 않고 바로 `200 OK`를 반환해야 함.
   - 또한 일반 승인 API 처리와 웹훅 수신이 동시에 들어올 수 있으므로 `Payment` 상태 전이 규칙과 락을 통해 한 번만 반영되도록 제어해야 함.
3. 빠른 응답 반환:
   - 웹훅 처리 시간이 길어져 타임아웃이 나면 토스가 계속 재전송하므로, 무거운 후속 작업은 비동기로 분리하고 웹훅 요청에는 빠르게 `200 OK`를 응답하는 구조가 적합함.

# 5주차 README

## 1. 코드 리팩토링 및 구조 개선

4주차에서 엔티티에 책임을 넘긴 데 이어, 이번에는 그동안 코드리뷰로 받은 피드백을 반영하여 인증·예매·결제 전반에서 계층 간 책임을 나누고 예외 처리와 트랜잭션 경계를 정리함.

### 1-1. 인증 (Spring Security & JWT 피드백 반영)

- 입력 검증: 요청 DTO에 `@NotBlank`, `@Email` 등 Bean Validation을 적용하고 컨트롤러에서 `@Valid`로 검증. 검증 실패는 `GlobalExceptionHandler`가 일관된 형식으로 응답.
- `logout` 보안 강화: 인가를 필수로 하고, 요청한 토큰의 소유자가 로그인한 사용자와 다르면 401로 거부함.
- 인가 설정 정리: `/api/auth/**` 전체 허용 대신 `signup`, `login`, `reissue` 3개 경로만 `permitAll`로 두고, 어드민 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")`로 보호함.
- 응답 형식 통일: 필터, `AuthenticationEntryPoint`, `AccessDeniedHandler`의 오류도 `GlobalExceptionHandler`를 거쳐 `ApiResponse` 단일 형식으로 내려가게 개선함.

### 1-2. 좌석·예매 도메인 개편

- 기존 문제
  - `ScreeningSeat`가 `Screening`에 직접 종속되어, 상영 회차를 만들 때마다 상영관 좌석 수만큼 row를 `INSERT`했고 같은 좌석 정보가 회차마다 중복 저장됨.
  - `Reservation`과 `ScreeningSeat`가 1:1이라 한 번에 여러 좌석을 예매하거나 일부만 취소할 수 없었음.

- 개선 구조
  - `Seat`: 상영관에 속한 물리 좌석 마스터. 상영관 생성 시 한 번만 만듦.
  - `SeatReservation`: 회차·좌석·예매를 잇는 점유 테이블. 예약된 좌석만 row로 저장함.
  - `Reservation` 1 : N `SeatReservation` 구조로 복수 좌석 예매와 부분 취소를 지원.

- 동시성 제어: 4주차의 비관적 락 대신 `(screening_id, seat_id)` 복합 UNIQUE 제약을 선택.
  - 좌석 도메인을 개편하여 빈 좌석은 `SeatReservation` row 자체가 없어 `FOR UPDATE`로 잠글 대상이 없게 됨.
  - 그렇다고 물리 좌석에 락을 걸면 서로 다른 회차(14:00, 19:00)의 같은 번호 좌석끼리도 불필요하게 대기하게 됨.
  - 복수 좌석은 `distinct().sorted()`로 항상 같은 순서로 `INSERT`해 락 순서를 맞추고, 유니크 위반은 `saveAndFlush` 후 `ALREADY_RESERVED`(409)로 변환함.

- 임시 점유(`HOLD`)와 만료
  - 예매 상태는 `HOLD`(5분) → `CONFIRMED` / `CANCELED` / `EXPIRED`로 전이하며, 각 전이 메서드가 현재 상태를 검증함.
  - 만료는 좌석 조회 시 지연 평가, 예매 시 만료 점유 해제, 스케줄러 정리의 세 경로로 처리하며, 스케줄러는 건별 `REQUIRES_NEW`로 한 건의 실패가 다른 건에 영향을 주지 않게 함.
  - 예매 시점의 `ticketPrice`와 `totalPrice`를 저장해 이후 가격이 바뀌어도 영향받지 않게 함.

### 1-3. 결제 트랜잭션 구조 개선

- 기존 문제 
  - `confirmPayment`, `cancelPayment` 전체가 `@Transactional`이라 토스 API 응답을 기다리는 동안 DB 커넥션과 락을 계속 점유함.
  - 승인 요청이 타임아웃되면 토스에서 실제로 승인됐는지 모른 채 실패로 처리됨.
  - `Payment`와 `Order`가 `@OneToOne`이라 결제가 취소·거절된 주문은 재결제할 수 없었음.
  - `/api/payments/**` 전체가 `permitAll`이라 인증 없이 접근 가능했음.

- 개선 내용
  - 책임 분리: 트랜잭션이 없는 오케스트레이터 `PaymentService`와, 짧은 트랜잭션만 담당하는 `PaymentTransactionService`로 나눔.
  - 실행 흐름: 사전 검증, `IN_PROGRESS → 토스 호출(DB 락 없음) → 승인 확정 또는 보상 취소
  - 락 순서: 대상(예매/주문) → `Payment` 순으로 통일해 교착 가능성을 줄임.
  - 타임아웃 복구: `IN_PROGRESS` 상태로 남은 결제는 토스 단건 조회로 실제 상태를 확인해 수렴시킴.
  - 재결제: `Payment`-`Order`를 `@ManyToOne`으로 바꿔 한 주문에 여러 결제 시도를 허용함.
  - 접근 제어: `/api/payments/**`의 `permitAll`을 제거하고 웹훅 경로만 허용하며, 결제 소유자 검증을 추가함.
  - 멱등키: 승인은 `orderId`, 보상 취소는 `COMPENSATION_`+`orderId`, 일반 취소는 클라이언트의 `cancelRequestId`를 토스 `Idempotency-Key`로 전달함.

- 예매와 결제 취소의 역할 분리
  - 결제가 끝난 예매는 예매 API로 취소할 수 없고, 결제 취소 API를 통해서만 취소됨.
  - 부분 취소 시 요청 금액을 서버가 계산한 값과 교차 검증해 위변조를 막음.

### 1-4. 웹훅을 통한 비동기 상태 동기화

- 필요한 이유: 사용자가 결제창에서 이탈해 `/confirm`이 호출되지 않거나, 운영자가 토스 콘솔에서 직접 환불한 경우를 서버 상태와 맞추기 위함. 
- 2중 검증
  - 1차: HMAC-SHA256 서명 검증. 비교는 `MessageDigest.isEqual`로 상수 시간 비교해 타이밍 공격을 방어함.
  - 2차: 웹훅 본문을 믿지 않고 `paymentKey`로 토스 단건 조회를 호출해 응답을 기준으로 처리함.
- 이벤트별 처리
  - `DONE`: 승인이 누락된 결제를 확정함. 이미 `DONE`이면 기존 결과를 반환(멱등). 5분 점유가 만료된 뒤 도착하면 좌석을 되살리지 않고 자동 환불 후 `ABORTED`로 기록하며, 토스에는 `200 OK`를 회신해 재전송을 막음.
  - `CANCELED`/`PARTIAL_CANCELED`: 잔액과 취소 이력을 동기화하고 `transactionKey`로 중복 반영을 방지함. 전액 취소는 좌석·재고를 복구하고, 콘솔 부분 취소는 어떤 품목인지 알 수 없어 경고 로그만 남김.
  - `ABORTED`/`EXPIRED`: `READY`·`IN_PROGRESS` 결제를 `ABORTED`로 종결함.


---

## 2. Docker 컨테이너화

### 2-1. 환경변수 정리 (`.env`)

- 기존에 여러 곳에서 관리되던 로컬 환경변수를 `.env`로 모으고, `docker compose up` 시 `docker-compose.yml`의 `${}`가 `.env` 값으로 채워지게 함. IntelliJ 실행 설정에도 같은 환경변수를 주입함.
- `.env`는 `.gitignore`로 제외하고, 견본 파일인 `.env.example`를 커밋하도록 함.

### 2-2. Dockerfile로 이미지 빌드 후 `docker run` 실행

- 사용한 실행 명령

```bash
docker build -t cgv-app .
docker compose up -d db
docker run --rm -p 8080:8080 --env-file .env -e DB_HOST=db --network spring-cgv-24th_cgv-net cgv-app
```

- `docker run`은 앱 컨테이너 하나만 띄우므로 compose와 달리 DB와 환경변수를 직접 챙겨야 함.
  - `--env-file .env`: `.env`의 변수를 컨테이너 환경변수로 주입함. compose의 `${}` 치환과 달리 파일 전체가 환경변수가 되고, 값의 따옴표도 그대로 포함됨.
  - `-e DB_HOST=db`: `.env`의 `DB_HOST`를 덮어씀. 컨테이너 안에서 `localhost`는 자기 자신을 가리키므로 DB 컨테이너의 서비스명을 써야 함.
  - `--network spring-cgv-24th_cgv-net`: compose가 만든 네트워크에 붙여야 서비스명 `db`로 DB를 찾을 수 있음. 이름은 `docker network ls`로 확인함.
- 빌드 시 `plain.jar` 제거
  - Spring Boot 2.5+는 빌드 시 실행용 `*.jar`(bootJar)와 내 클래스만 담긴 `*-plain.jar`를 함께 만듦.
  - Dockerfile의 `COPY build/libs/*.jar app.jar`가 파일 2개와 매칭되어 빌드가 실패하므로, `build.gradle`에서 `jar` 태스크를 비활성화해 실행용 jar만 생성하게 함.

### 2-3. docker-compose로 앱 + DB 실행

- `db`(MySQL 8.0)와 `backend`(Spring 앱)를 한 번에 실행함.
- MySQL 이미지는 `MYSQL_` 접두사 변수만 읽으므로 `MYSQL_DATABASE`, `MYSQL_ROOT_PASSWORD`로 전달하고 root 계정만 사용하도록 함.
- `backend`에는 `DB_HOST: db`를 compose에서만 덮어써, 컨테이너 안에서는 서비스명으로 DB에 접속하게 함.
- `db`에 healthcheck를 추가하고 `depends_on`을 `service_healthy` 조건으로 설정해, DB가 준비된 뒤 앱이 시작되도록 설정함.
- `.dockerignore`로 `.env`, `.git`, `.idea` 등을 빌드 컨텍스트에서 제외함.

### 2-4. 실행·재실행 시 주의할 점

- 최초 실행: `docker compose up --build`
- 발생했던 이슈
  - 초기에는 Dockerfile이 로컬 `build/libs`의 jar를 그대로 복사해서, `bootJar` 없이 `up`만 하면 옛 코드가 담긴 이미지가 재사용되고 실행 실패함.(DB 연결 문제 코드가 남아있었기 때문)
  - Dockerfile을 멀티스테이지 방식으로 바꾸면서 컨테이너 안에서 항상 새로 빌드하게 되어 해결됨.
- DB 설정 변경: MySQL 이미지는 데이터 디렉토리가 비어 있을 때만 `MYSQL_*` 값을 적용하므로, 비밀번호나 DB 이름을 바꿨다면 `docker compose down -v`로 볼륨을 지운 뒤 다시 실행해야 함. 

---

## 3. CI/CD 파이프라인

### 3-1. 전체 구조: Actions는 CI, Railway는 CD

- 배포 흐름
  1. `master`에 push하거나 `master` 대상 PR을 올림.
  2. GitHub Actions가 JDK 21 환경에서 `./gradlew test`를 실행함.
  3. 테스트가 통과하면 Railway가 해당 커밋을 Dockerfile로 직접 빌드하고 배포함.

- 역할 분담
  - GitHub Actions(CI): 코드를 검증하는 역할만 맡음.
  - Railway(CD): 이미지를 빌드하고 서버에 올리는 역할. GitHub 저장소와 연동한 뒤 "Wait for CI"를 켜서, Actions 결과를 기다린 뒤 배포하도록 설정함.

### 3-2. 멀티스테이지 Dockerfile

- 기존 방식의 문제
  - 원래는 로컬에서 미리 빌드한 `build/libs/*.jar`를 이미지에 복사하는 구조였음.
  - 근데 `build/`는 `.gitignore` 대상이라 GitHub에는 jar가 없음. 따라서 Railway처럼 GitHub에서 코드를 받아 빌드하는 환경에서는 `COPY`가 실패함.

- 변경 후
  - 1단계(build): `eclipse-temurin:21-jdk-alpine`에서 `./gradlew bootJar -x test`로 실행 jar를 빌드함. (테스트는 CI 단계에서 함)
  - 2단계(실행): `eclipse-temurin:21-jre-alpine`에 1단계의 jar만 복사. JDK, Gradle, 소스는 최종 이미지에 포함되지 않아 이미지가 가벼워짐.
  - 이제 `./gradlew bootJar` 없이 `docker build` 또는 `docker compose up --build`만으로 실행됨.

- 빌드 속도를 위한 구성
  - `build.gradle`과 Gradle wrapper를 소스보다 먼저 복사해 의존성 다운로드를 별도 레이어로 캐시함. 소스만 바뀌면 의존성 레이어를 재사용함.
  - `.dockerignore`에 `build`를 추가해 로컬 빌드 산출물이 컨텍스트에 섞이지 않게 함.

### 3-3. GitHub Actions 워크플로 (`ci.yml`)

- 실행 조건: `master`에 push되거나 `master`를 대상으로 PR이 올라오면 실행됨.
- 구성 단계
  1. `actions/checkout`: 저장소 코드를 runner로 가져옴.
  2. `actions/setup-java`: JDK 21(Temurin)을 설치함.
  3. `gradle/actions/setup-gradle`: Gradle 실행 환경을 구성하고 의존성을 캐싱함.
  4. `./gradlew test`: 전체 테스트를 실행함. 테스트는 `src/test/resources/application.yaml`의 H2 설정을 사용하므로 별도 DB 없이 실행됨.
- 최소 권한: `permissions: contents: read`로 코드 읽기 권한만 부여함.

### 3-4. 테스트 정리 (CI 도입 전 이슈)

CI를 켜기 전 로컬에서 `./gradlew test`를 실행해 보니 테스트가 통과하지 않았음. 

- 원인: 컴파일 불가 테스트
  - 좌석 도메인 개편으로 사라진 `ScreeningSeat`, `ScreeningSeatRepository` 등을 테스트가 참조하여 `compileTestJava`가 실패.
  - 컴파일이 안 되는 테스트 6개와, 없어진 `SeatRepository` mock 때문에 NPE가 나던 `ScreenAdminServiceTest`를 삭제함.
  - 예매·결제 테스트는 개편된 도메인에 맞게 이후 추가해 나갈 예정.

### 3-5. Railway 배포 설정 및 동작 확인

- 환경변수
  - 배포 환경에는 `.env`가 없으므로 DB, JWT, 관리자 토큰, 토스 키 등은 Railway의 Variables에 직접 등록해 주입함.
- PORT 지원
  - Railway는 `PORT` 환경변수로 컨테이너가 사용할 포트를 지정하므로 `application.yaml`에 `server.port: ${PORT:8080}`을 추가함. 
- 동작 확인
  - push부터 CI 테스트, 배포까지 자동으로 동작하는 것을 확인함.
  - 배포된 서버에 Postman으로 요청을 보내 정상 응답을 확인함.
