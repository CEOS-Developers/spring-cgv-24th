# spring-cgv-24th
CEOS 24기 백엔드 스터디 - CGV 클론 코딩 프로젝트

## 2주차 DB 모델링
<details>
<summary>CGV DB 모델링</summary>
<div markdown="1">

![img.png](img.png)
### **회원 도메인 - `Member`**

모든 행위의 주체입니다. 예매, 구매, 찜  3개의 도메인이 전부 회원 테이블과 연관관계(1:N)를 가집니다. 추후 이메일, 소셜 UUID 등 컬럼을 추가할 예정입니다.

### **영화 도메인 - `Movie`, `Cinema`, `Screen`, `ScreenType`, `Screening`**

영화와 영화관의 M:N 관계를 풀기 위해 중간에 Screen(상영관), Screening(상영회차) 중간 테이블을 두었습니다.

최종적으로 **영화 -1:N - 상영회차 - N:1 - 상영관 -N:1 - 영화관** 구조를 설계했습니다.

**`Screening` 테이블**: 하나의 영화는 여러 상영관에서 상영될 수 있고, 하나의 상영관을 여러 영화가 상영될 수 있으므로 중간 테이블을 두어 N:M 관계를 해소하고자 하였습니다.

`SceenType` **테이블**: “종류가 같다면 좌석을 동일하다” → 좌석 배치가 개별 상영관이 아니라 상영관 종류에 딸린 속성입니다. 그래서 좌석을 상영관 종류 안에 넣었습니다.

### **예매 도메인: `Reservation`, `Reservation_seat`**

예매는 영화가 아닌 상영회차를 기리킵니다. 상영회차만 알면, N:1의 흐름을 따라 영화, 상영관, 영화관 정보를 알 수 있습니다.

예매 좌석을 별도 테이블로 둔 이유는 개수입니다. 한 예매에 회원과 상영회차는 하나씩이지만 좌석은 2개를 사면 2개가 되어야합니다. 아래 상품 구매와 구매 항목 테이블 관계와 같습니다.

또한 상영 회차 테이블과의 관계를 따로 추가했습니다. 그 이유는 `(screeing_id, seat_id)` 유니크 제약을 걸기 위해서입니다.

상영회차 FK가 없을 경우에 만약 A(id=7)와 B(id=8)가 동시에 예매를 했다면,

예매좌석 (id=7, 회차=2, 좌석=I행 1열), 예매 (id=8, 회차=2, 좌석=I행 1열)

→ 같은 회차에 대해 중복 좌석이 예약된다.

### **상품 도메인: `Menu`, `Stock`, `Purchase`, `Purchase_item`**

“영화관마다 매점이 있으며 재고를 따로 관리해요” → 영화관:매점을 1:1 관계이지만, “모든 영화관의 메뉴는 같다”이므로 메뉴는 전역 테이블로 설정, 재고는 따로 테이블을 두었기 때문에, 매점 테이블에 남을 컬럼이 `id`와 `cinema_id` 뿐이다. 그래서 매점이라는 개념을 영화관 테이블에 흡수시켰습니다.

상품 구매를 `Purchase`와 `Purchase_item`으로 나눈 이유도 앞선 예매 좌석을 따로 만든 이유와 같습니다.

### **좋아요 도메인 - `Movie_like`, `Cinema_like`**

두 테이블 모두 (member_id, 대상_id) 유니크가 필요합니다. (중복 예방)

</div>
</details>

<details>
<summary>테스트 환경 세팅</summary>
<div markdown="1">

### 테스트 환경을 H2 DB로 세팅

→ 장점: 속도가 빠르다.

→ 단점: 동시성 테스트를 진행하면 mySQL과 H2 실행결과가 다를 수 있다.

### 그래서 mySQL 테스트 스키마를 생성

→ 장점: 실제 운영 테스트와 실행 결과가 같다

→ 단점: .env 파일을 주입시켜야지 application-test.yaml이 환경변수를 읽을 수 있는데, 현재는 gradle 러너가 테스트를 주관하므로, 환경변수를 주입할 방법이 없다.

만약 Intellij 러너로 바꾸면 환경변수를 주입할 수는 있겠지만, 다른 사람이 cli에서 gradle test를 진행할 경우에는 테스트가 불가하다.

### 결국
결국 mySQL 테스트 스키마를 생성하고, gradle 러너가 .env 환경변수를 읽을 수 있도록 build.gradle 파일에 다음과 같은 코드로 설정하여, .env를 읽을 수 있도록 했다.

```java
tasks.named('test') {
	useJUnitPlatform()

	// .env 값을 테스트 JVM 환경변수로 주입한다.
	def envFile = file('.env')
	if (envFile.exists()) {
		envFile.readLines().each { line ->
			def matcher = line =~ /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/
			if (matcher.matches()) {
				environment matcher.group(1), matcher.group(2).replaceAll(/^["']|["']$/, '')
			}
		}
	}
}
```
</div>
</details>


<details>
<summary>2주차 세션 학습 정리</summary>
<div markdown="1">

### 1. Proxy와 N+1 문제의 관계

프록시를 사용하는 이유는 “필요할 때까지 미뤄뒀다가, 필요해지면 가져온다”(`지연로딩`)인다.

근데, 만약 `teamRepository.findById(1L)`을 통해 Team 객체를 찾고, 해당 Team 속한 모든 Member를 조회한다면 → 왜 굳이 프록시를 사용해서 매 Member마다(`N번`) 프록시를 초기화하면서 쿼리를 날리는 거지?

그래서 결국, N+1 문제가 발생하면 지연 로딩을 선택한 이득이 사라지게 되는 것이다. N+1 문제를 원천 차단할 거라면 `fetch join`을 통해 프록시를 도입한 이점을 최대한 살려야 한다.

### 2. Hibernate Proxy와 Spring AOP Proxy의 차이?

```java
@Entity
public class Post {
	@Id
	Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	Member author
}
```

`post.getAuthor()`를 호출하면 실제 Member가 아니라 하이버네이트가 런타임에 만든 상속 클래스가 들어있다. 이 객체는 식별자(`id`)만 갖고 있다. → 이게 하이버네이트 프록시.

**Hibernate 프록시**: 아직 안 가져온 데이터. 즉 빈 껍데기일 뿐이다.

**Spring AOP 프록시**: 원랙 객체(`빈`) + 부가 기능을 덧씌운 포장지이다.

### 3. fetch join을 사용하면서 페이징을 적용할 때 발생하는 문제

컬렉션을 fetch join하면 페이징이 메모리에서 동작한다.

→ 조인 결과는 행이 무분별하게 많아지기 때문이다. `Post`가 3개, 각각 댓글이 4, 3, 2개라면 조인 결과는 9행이 된다. 여기에 `limit 2`를 걸면 Post 2개가 아니라 Post 1번의 댓글 2개만 잘린다.

Hibernate는 이걸 하기 때문에 `limit`을 SQL에 넣지 않는다. 대신 전체를 다 읽어와서 애플리케이션 메모리에서 중복 제거 후 잘라낸다. → 결국 OOM이 발생하게 된다.(`MultipleBagFetchException`)

그래서 해결책 = **배치 사이즈**

컬렉션은 fetch join하지 말고, batch 단위로 가져온다.

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

```java
List<Post> posts = postRepository.findAll();  // 100건, Member는 LAZY

for (Post post : posts) {
    post.getAuthor().getName();
}
```

- Member를 in절로 해서 100건을 다 가져온다.

  `select * from Member where id in ( 100 개)`

</div>
</details>
