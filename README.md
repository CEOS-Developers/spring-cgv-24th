
# 1. DB 설계 설명
![DB 설계](images/db_design.png)

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

# 3주차 README

## 1. **JWT를 활용한 인증 흐름 정리하기**

#### 1. JWT의 Header, Payload, Signature는 각각 어떤 역할을 하나요?

- Header: 토큰의 메타데이터(암호화 알고리즘과 토큰의 타입)를 담는다.
- Payload: 실제 전달하려는 Claim 데이터를 담는다. 유저 식별자(PK), 토큰 발급 및 만료 시간, 권한 등의 실질적인 정보를 포함한다. Base64로 인코딩될 뿐 암호화되지 않으므로 민감한 정보를 포함해서는 안 된다!
- Signature: 토큰의 위변조 여부를 검증한다. 서버가 가진 Secret Key를 이용하여 Header와 Payload를 암호화해 생성한다. Payload가 변조될 경우 서명이 일치하지 않아 서버에서 유효하지 않은 토큰으로 판단하고 차단한다.

#### 2. Access Token과 Refresh Token은 무엇이 다른가요?

- Access Token: 서버의 API 리소스에 접근할 때마다 헤더에 포함하여 전송하는 실질적인 자격 증명 수단이다. 탈취 위험을 최소화하기 위해 유효 기간을 짧게 설정한다.
- Refresh Token: 유효 기간이 길며, 만료된 Access Token을 새로 발급받기 위한 용도로만 사용한다. 탈취 시의 위험을 줄이기 위해 데이터베이스나 Redis에 저장하여 상태를 관리하며(RTR
  등), 일반적인 API 리소스 접근에는 사용할 수 없다!

#### 3. 쿠키와 세션, JWT는 각각 어떤 역할을 하나요?

- 쿠키: 브라우저(클라이언트) 단에 데이터를 저장하는 매체이다. 세션 ID를 보관할 수도 있고,
  JWT를 보관할 수도 있으며, 매 HTTP 요청 시 서버로 자동 전송된다.
- 세션: 유저의 상태를 서버의 메모리에 저장하는 방식이다. 브라우저에는 고유한 세션 ID만
  쿠키로 발급한다. 보안성은 높지만, 동시 접속자가 많아지면 서버 메모리 부하가 증가하고 다중
  서버(Scale-out 시) 환경에서 관리가 까다롭다.
- JWT: 유저의 정보를 암호화 및 서명하여 토큰 자체에 저장하는 Stateless한 인증 방식. 서버는 메모리에 상태를 저장하지 않으므로 서명 검증만으로 인증이 가능하여 서버 확장에 매우 유리하다.

#### 4. CGV 프로젝트의 Access Token에는 어떤 Claim이 필요한가요?

저는 현재 아래 5가지 클레임으로 토큰을 구성해봤습니다.

- iss (Issuer): 토큰 발급자. 현재 "http://localhost:8080"으로 고정되어 있으며, 파싱 시 requireIssuer()를 통해 발급자가 일치하는지 검증한다.
- sub (Subject): memberId를 담는다. 필터에서 이 값을 추출해 데이터베이스에서 회원 정보를 조회하는 데 사용한다.
- type: 해당 토큰이 Access Token인지 Refresh Token인지 구분하는 사용자 정의 클레임. Refresh Token을 헤더에 넣어 일반 API 리소스에 접근하는 것을 원천 차단하기 위해 추가하였음.
- iat (Issued At): 토큰이 발급된 시간 정보.
- exp (Expiration Time): 토큰의 만료 시간.

매번 memberId를 통해 DB를 조회하여 권한 확인 하는 과정을 개선하기 위해, Role 클레임을 넣어 DB 조회 없이 권한을 확인하게 한다거나, 블랙리스트 기능을 위한 jti(JWT ID) 등을 추가해도 좋을 것 같네요.

#### 5. JWT 검증 결과가 Authentication과 SecurityContext로 어떻게 연결되나요?

1. JwtAuthenticationFilter에서 토큰의 서명을 검증하고 memberId를 추출한다.
2. 추출한 정보로 CustomUserDetails 객체를 생성한 뒤, 이를 UsernamePasswordAuthenticationToken으로 래핑한다.
3. 생성된 Authentication 인증 객체를 스프링 시큐리티의 전역 저장소인 SecurityContextHolder 내의 SecurityContext에 저장한다.
4. 이후 컨트롤러 단에서 @AuthenticationPrincipal 어노테이션을 통해 SecurityContext에 저장된 유저 정보를 꺼내어 사용할 수 있다.

#### 6. 인증과 인가는 어떻게 다르며, 401과 403은 각각 언제 반환하나요?

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

## 2. 테스트 결과

- 올바른 로그인 정보

![Screenshot](images/Screenshot_2026-09-21_at_7.07.34_PM.png)

200 OK, Access Token 정상 발급

- 존재하지 않는 ID로 로그인

![Screenshot](images/Screenshot_2026-09-21_at_7.13.56_PM.png)

401 Unauthorized, 토큰 미발급

- 잘못된 비밀번호로 로그인

![Screenshot](images/Screenshot_2026-09-21_at_7.13.29_PM.png)

401 Unauthorized, 토큰 미발급

- 토큰 없이 공개 API 호출

![Screenshot](images/Screenshot_2026-09-21_at_7.43.11_PM.png)

200 OK, 전체 영화 조회 등의 공개 API는 토큰 없이도 접근 가능하도록 설정

- 정상 토큰으로 보호된 API(찜한 영화 등록) 호출

![Screenshot](images/Screenshot_2026-09-21_at_7.54.59_PM.png)

201 CREATED, 정상 호출

- 토큰 없이 보호된 API(찜한 영화로 등록) 호출

![Screenshot](images/Screenshot_2026-09-21_at_7.58.00_PM.png)

401 Unauthorized, 공통 응답 처리

- 만료된 토큰으로 보호된 API(찜한 영화로 등록) 호출

![Screenshot](images/Screenshot_2026-09-21_at_8.00.11_PM.png)

401 Unauthorized, “만료된 토큰입니다.”

- 변조된 토큰으로 보호된 API(찜한 영화로 등록) 호출

![Screenshot](images/Screenshot_2026-09-21_at_8.02.52_PM.png)

401 Unauthorized, “유효하지 않은 토큰입니다”

- 다른 키로 서명한 토큰

![Screenshot](images/Screenshot_2026-09-21_at_9.00.40_PM.png)

로그인하여 토큰을 발급받고,

![Screenshot](images/Screenshot_2026-09-21_at_9.06.30_PM.png)

환경변수로 등록된 시크릿 키를 변조(맨 앞에 1 추가)한 다음 찜한 영화 등록 요청을 보내면

![Screenshot](images/Screenshot_2026-09-21_at_9.07.31_PM.png)

401 Unauthorized, “유효하지 않은 토큰입니다.”

- 일반 사용자로 관리자 API(영화 등록) 호출

![Screenshot](images/Screenshot_2026-09-21_at_8.05.06_PM.png)

403 Forbidden, 공통 응답 Json

- 관리자로 관리자 API(영화 등록) 호출
    
  ![Screenshot](images/Screenshot_2026-09-21_at_8.49.56_PM.png)


201 Created

- 정상 인증 요청 직후, 토큰 없이 보호된 API 호출

![Screenshot](images/Screenshot_2026-09-21_at_8.56.50_PM.png)

401 Unauthorized, “토큰이 존재하지 않습니다”

- 다른 사용자의 데이터 접근 (다른 사용자가 예매한 좌석 예매 취소 시도)

![Screenshot](images/Screenshot_2026-09-26_at_5.36.18_PM.png)

404 Not Found, "존재하지 않는 예매이거나 권한이 없습니다.”

- CORS 설정과 Preflight 요청

![Screenshot](images/Screenshot_2026-09-28_at_2.33.29_PM.png)

적용 전, 401 Unauthorized, “토큰이 존재하지 않습니다”

- OPTIONS 메서드
- 헤더 두 개 추가
    - Key: Origin, Value: http://localhost:3000
    - Key: Access-Control-Request-Method, Value: POST

![Screenshot](images/Screenshot_2026-09-28_at_2.34.07_PM.png)

적용 후, 200 OK


# 4주차 README

## 1. 객체지향 코드 리팩토링

기존 코드는 엔티티가 단순히 데이터만 담고 있는 바구니에 가까웠고, 서비스 계층에서 데이터를 꺼내 직접 계산하거나 상태를 바꾸는 방식이 많았습니다. 
이번 리팩토링에서는 객체가 스스로의 데이터를 다루도록 역할을 넘기고 도메인의 책임을 강화하는 데 집중했습니다.

### 1-1. 주문 도메인 (Order, OrderItem)

- OrderItem 캡슐화와 계산 책임 부여
  - 기존에는 OrderItem이 가격과 수량만 필드로 가지고 있을 뿐, 소계 금액(가격 * 수량)을 계산하는 로직이 없어서 OrderService가 getter로 값을 꺼내 계산했습니다.
  - 이를 OrderItem 내부의 calculateSubtotal 메서드로 옮겨 객체가 스스로 자신의 금액을 계산하도록 변경했습니다.
  - 또한 생성자를 닫고 정적 팩터리 메서드를 도입하여 메뉴나 수량에 잘못된 값이 들어오지 않도록 생성 시점에 유효성 검증을 거치도록 했습니다.

- Order와 영속성 전이 적용
  - 주문 상태 변경이나 총 결제 금액 계산 로직을 Order 엔티티 내부로 응집시켰습니다.
  - Order와 OrderItem 사이에 CascadeType.ALL과 orphanRemoval을 적용하여, 주문 항목의 저장과 삭제 등 생명주기를 루트 엔티티인 Order에 맡기도록 개선했습니다. 덕분에 서비스 계층에서 OrderItemRepository를 직접 주입받아 일일이 저장할 필요가 없어졌습니다.

### 1-2. 예매 및 좌석 도메인 (Reservation, Seat)

- Seat 도메인의 상태 제어
  - 기존에는 외부에서 좌석의 예약 여부를 자유롭게 변경할 수 있어 무결성이 깨질 위험이 있었습니다.
  - 이를 reserveSeat와 cancelSeat 같은 명확한 도메인 메서드로 제한하고, 이미 예약된 좌석을 다시 예약하려고 하면 엔티티 내부에서 예외를 던지도록 캡슐화했습니다.

- Reservation 검증 로직 이전
  - 예매 생성 시 좌석 상태를 확인하고 예약자로 등록하는 흐름을 도메인 내부에서 검증하도록 정리하여, 서비스 코드가 단순하고 읽기 쉬운 흐름을 유지하도록 했습니다.


---

## 2. 동시성 문제 분석과 해결

여러 사용자가 동시에 시스템을 이용할 때 데이터가 꼬이거나 의도치 않은 결과가 나오는 동시성 문제를 분석하고 제어했습니다.

### 2-1. 서비스 내 동시성 문제가 발생하는 지점
 
이전 CGV 서비스에서는 크게 두 군데에서 동시성 문제가 발생할 수 있습니다.

1. 매점 메뉴 재고 차감
   - 동일한 팝콘 메뉴의 재고가 10개 남아있을 때, 두 사용자가 동시에 주문을 시도하면 둘 다 10개라는 재고를 읽어옵니다.
   - 각자 1개씩 주문하여 재고를 9개로 만들어 저장하면, 실제로는 2개가 팔렸는데 재고는 9개가 되는 갱신 분실 문제가 생깁니다. 

2. 상영관 좌석 예매 
   - 특정 좌석을 두 사용자가 찰나의 순간에 동시에 선택할 때 발생합니다.
   - 두 요청 모두 좌석이 비어있다고 판단하고 예매를 진행하게 됩니다. DB 유니크 제약조건이 걸려있다면 2중 예매는 막히겠지만, 늦게 도착한 사용자에게 비즈니스 안내 메시지가 아니라 알 수 없는 DB 에러가 터지게 됩니다.

### 2-2. 동시성 해결 방법 비교

| 방법 | 장점 | 단점 및 한계 |
|---|---|---|
| synchronized | 자바 언어 차원에서 제공하여 구현이 간단함 | 스프링의 @Transactional 프록시 환경에서는 트랜잭션 커밋 전에 락이 먼저 풀려 다른 스레드가 과거 데이터를 읽을 수 있음. 서버가 여러 대로 늘어나면 작동하지 않음 |
| 낙관적 락 | 실제 DB 락을 걸지 않아 읽기/쓰기 성능이 좋고 데드락이 없음 | 충돌이 자주 나면 롤백과 재시도가 반복되어 오히려 DB 부하가 커지고 코드가 복잡해짐 |
| 비관적 락 | DB 배타 락을 걸어 충돌을 원천 차단하므로 데이터 정합성이 확실함 | 락을 점유하는 동안 다른 요청이 대기해야 하므로 처리량이 떨어질 수 있음 |
| Redis 분산 락 | DB 부하 없이 빠르고 다중 서버 환경에서도 동작함 | 별도의 Redis 인프라 구축과 관리 비용이 발생함 |

### 2-3. 비관적 락을 선택한 이유와 해결 과정

- 선택 근거
  - 매점 재고는 한정된 자원이고 동시 주문이 몰리는 특성이 있습니다. 충돌 확률이 높은 상황에서 낙관적 락을 쓰면 수많은 롤백과 재시도가 일어나 오히려 비효율적이라고 보았습니다.
  - 또한 현재 서비스 규모에서 Redis 인프라를 추가하는 것은 다소 오버엔지니어링이라고 판단했습니다.
  - 따라서 데이터 정합성이 최우선인 재고 차감에는 DB 레벨에서 순차적으로 줄을 세워 처리하는 비관적 배타 락이 가장 알맞다고 결정했습니다.

- 해결 과정
  - 메뉴 재고를 조회하는 저장소 메서드에 비관적 락 어노테이션을 적용하여 SELECT FOR UPDATE 쿼리가 나가도록 했습니다.
  - 멀티스레드 환경에서 100명이 동시에 1개씩 주문하는 테스트를 작성해 확인해보니, 락을 적용하기 전에는 갱신 분실로 인해 재고가 90개 넘게 남아있었지만, 비관적 락을 적용한 뒤에는 100번의 차감이 정확히 순차 처리되어 재고가 0개로 정확히 맞아떨어졌습니다.


---

## 3. 토스페이먼츠 결제 시스템 연동

매점 주문에 대해 토스페이먼츠 테스트 환경을 연동하여 결제 라이프사이클을 완성했습니다.

### 3-1. 주문 및 결제 흐름과 상태 머신

- 결제 흐름
  1. 클라이언트가 주문 생성 요청을 보내면 서버에 주문과 함께 READY 상태의 결제 데이터가 생성됩니다.
  2. 클라이언트가 토스 결제창을 띄워 카드 인증을 마치고 paymentKey와 주문 정보를 받아옵니다.
  3. 클라이언트가 서버로 결제 승인을 요청합니다.
  4. 서버는 금액과 주문 무결성을 검증한 뒤 토스페이먼츠 승인 API를 호출합니다.
  5. 승인이 완료되면 결제 상태를 DONE으로 바꾸고 잔액을 반영합니다.

- 결제 상태 전이
  - READY: 주문서 생성 후 결제 인증 대기
  - DONE: 토스 승인이 완료된 정상 결제 상태
  - PARTIAL_CANCELED: 결제 금액 중 일부만 취소되어 잔액이 남아있는 상태
  - CANCELED: 결제 전액이 취소된 상태
  - ABORTED: 결제 승인 실패 또는 중단 상태

### 3-2. 결제 승인 전 서버 검증 (클라이언트 요청을 믿지 않는 이유)

- 왜 클라이언트 데이터를 그대로 믿으면 안 될까?
  - 브라우저나 모바일 앱은 개발자 도구 또는 네트워크 프록시 툴을 통해 요청 값을 얼마든지 가로채고 바꿀 수 있습니다.
  - 악의적인 사용자가 15,000원짜리 팝콘 세트를 주문해놓고, 토스 결제창 인증이나 승인 요청 시 amount를 100원으로 변조하여 보낼 위험이 있습니다.

- 서버에서 수행한 3단계 검증
  1. 주문 존재 및 주문자 일치 여부 확인
  2. 우리 DB에 저장된 주문의 실제 총 금액과 클라이언트가 보낸 요청 금액이 정확히 일치하는지 비교 (불일치 시 즉시 차단)
  3. 해당 주문이 이미 결제 완료(DONE)된 상태인지 확인하여 중복 승인 방지

이 검증을 통과한 요청만 토스페이먼츠 승인 API로 전달되도록 설계했습니다.

### 3-3. 결제 조회와 역할 분담

결제 내역을 조회할 때마다 매번 토스 API를 호출하는 것은 불필요한 네트워크 비용을 발생시키고, 토스에 장애가 나면 우리 서비스의 조회 기능까지 마비됩니다. 
따라서 승인 시점에 결제 식별 키, 결제 수단, 승인 일시, 최종 금액 등의 핵심 데이터를 우리 DB에 저장해두고 조회를 처리하도록 역할을 나누었습니다. 토스는 카드사 망과의 승인 및 정산 책임을 맡고, 우리 서버는 주문 및 결제 이력 관리 책임을 맡습니다.

### 3-4. 전체 취소 및 부분 취소와 잔액 관리

- 잔액 관리
  - 결제 엔티티에 최초 승인 금액과 별개로 현재 취소 가능한 남은 잔액 필드를 두었습니다.
  - 부분 취소가 들어오면 남은 잔액에서 취소 요청 금액을 차감하고, 잔액이 0원이 되면 상태를 CANCELED로, 잔액이 남아있으면 PARTIAL_CANCELED로 변경합니다.

- 취소 이력 기록
  - 취소가 발생할 때마다 별도의 취소 이력 엔티티에 취소 사유, 취소 금액, 일시를 기록하여 여러 번의 부분 취소가 일어나도 전체 히스토리를 투명하게 추적할 수 있도록 했습니다.
  - 이미 전액 취소된 결제에 다시 취소를 요청하거나, 남은 잔액보다 큰 금액을 취소하려는 비정상 요청은 도메인 검증 단계에서 예외로 차단했습니다.


---

## 4. 중복 결제와 멱등성

네트워크 지연이나 사용자의 중복 클릭으로 인해 동일한 요청이 여러 번 들어오는 상황을 방어했습니다.

### 4-1. 동시성 제어와 멱등성의 차이

- 동시성 제어: 동일한 찰나의 순간에 여러 스레드가 동시에 자원에 접근할 때 순서를 제어해 데이터 정합성을 지키는 것입니다.
- 멱등성: 시간차를 두고 동일한 요청이 두 번 이상 들어와도, 시스템의 최종 상태가 처음 한 번 실행했을 때와 동일하게 유지되는 성질입니다.

둘 중 하나만 있어서는 결제 사고를 막을 수 없습니다. 동시성 제어만 되어 있다면 사용자가 2초 간격으로 결제 버튼을 두 번 눌렀을 때 두 번 다 정상 처리되어 이중 출금이 발생합니다. 반대로 멱등성 검사만 있고 락이 없다면, 거의 같은 시각에 두 요청이 들어왔을 때 둘 다 검증을 통과해버릴 수 있습니다. 따라서 두 안전장치가 함께 동작해야 합니다.

### 4-2. 결제 승인 및 취소에서 멱등성을 보장한 방법

- 승인 멱등성
  - 네트워크 타임아웃 등으로 클라이언트가 승인 요청을 재전송했을 때, 이미 해당 주문의 결제 상태가 DONE이라면 토스 승인 API를 다시 호출하지 않고 기존에 저장된 결제 성공 응답을 그대로 반환하도록 구현했습니다.

- 취소 멱등성
  - 토스페이먼츠의 취소 API를 호출할 때 가맹점이 고유하게 생성한 키를 Idempotency-Key 헤더에 실어 보냄으로써, 네트워크 재시도가 발생하더라도 토스 측에서 중복 환불이 일어나지 않도록 처리했습니다.


---

## 5. 외부 API 통신 고민과 예외 처리

토스페이먼츠와 통신하기 위한 클라이언트를 선택하고 장애 상황에 대응했습니다.

### 5-1. RestClient를 선택한 이유 

- RestTemplate: 가장 오래 쓰여 익숙하지만, 현재 스프링에서 신규 기능 추가를 중단한 유지보수 모드 상태이고 코드가 장황합니다.
- WebClient: 논블로킹 방식으로 대용량 처리에 좋지만, 리액티브 기반이라 현재 서블릿 기반 스프링 MVC 프로젝트에 도입하기에는 불필요한 의존성이 추가되고 복잡도가 올라갑니다.
- OpenFeign: 인터페이스만으로 선언할 수 있어 간편하지만, Spring Cloud 관련 무거운 의존성이 필요합니다.
- RestClient (선택): 스프링 3.2에서 도입된 최신 동기 클라이언트입니다. 추가 라이브러리 없이 기본 제공되며, WebClient처럼 체이닝 방식으로 코드가 깔끔하고 동기식 서블릿 환경과 트랜잭션 흐름에 가장 잘 맞아 최종 선택했습니다.

### 5-2. 외부 API 장애 및 네트워크 오류 대응

- 토스 API 호출 실패 시
  - 네트워크 단절이나 토스 서버 장애로 에러가 발생하면, 무작정 500 에러를 띄우지 않고 결제 연동 실패라는 비즈니스 에러로 전환하여 클라이언트에게 명확한 사유를 전달하도록 처리했습니다.

- 결제 성공 후 서버 오류 시 상태 불일치 고려
  - 토스에서는 카드 승인이 완료되었는데 우리 서버 DB에 저장하는 과정에서 예외가 발생하면 고객 돈은 빠져나갔는데 주문은 실패하는 심각한 불일치가 생깁니다.
  - 이를 방지하기 위해 DB 트랜잭션 롤백 시 토스 취소 API를 호출하여 결제를 자동 무효화하는 보상 트랜잭션의 필요성을 정리해두었습니다.


---

## 6. Webhook 알아보기

### 6-1. Polling 대신 Webhook을 사용하는 이유

- Polling 방식: 클라이언트나 우리 서버가 주기적으로 "결제 끝났나?" 계속 확인하는 방식입니다. 상태가 바뀌지 않아도 계속 요청을 보내므로 네트워크 트래픽과 서버 자원이 크게 낭비됩니다.
- Webhook 방식: 토스에서 결제 완료나 취소 같은 이벤트가 발생했을 때 우리 서버로 직접 알려주는 Push 방식입니다. 변경이 있을 때만 딱 한 번 호출되므로 리소스 낭비가 없고 즉각적인 처리가 가능합니다.

### 6-2. CGV 서비스에 Webhook을 도입한다면 맡길 역할

1. 이탈 고객의 결제 상태 동기화
   - 사용자가 카드 인증을 완료했는데 브라우저 창을 닫아버리거나 네트워크가 끊겨 서버로 승인 요청이 오지 못하는 경우가 있습니다. 웹훅이 연결되어 있다면 토스 측에서 결제 상태 변경을 알려주므로 누락 없이 주문을 살릴 수 있습니다.

2. 비동기 결제 수단 지원
   - 가상계좌처럼 고객이 나중에 입금하는 방식은 승인이 즉시 나지 않습니다. 고객이 입금을 완료했을 때 토스가 웹훅으로 알려주면 그때 예매나 주문을 확정 처리하는 핵심 안전장치 역할을 하게 됩니다.

