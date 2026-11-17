# 로컬 모니터링 시스템 구축 및 관리

이 문서는 CGV 프로젝트에 적용한 Spring Boot Actuator, Micrometer, Prometheus, Grafana 구성을 설명하고 로컬 실행, 대시보드 관리, 복원 및 문제 해결 방법을 정리합니다.

## 1. 구성과 역할

```text
Spring Boot 앱 + MySQL
  └─ Actuator / Micrometer: 앱 상태 및 지표 제공
       ↑ Prometheus가 /actuator/prometheus를 15초마다 조회
     Prometheus: 지표 수집 및 시계열 저장
       ↑ Grafana가 PromQL로 조회
     Grafana: 대시보드 시각화
```

| 구성 요소 | 역할 |
| --- | --- |
| Actuator | 상태 확인 및 메트릭 조회용 HTTP 엔드포인트 제공 |
| Micrometer | JVM, HTTP 요청, 커넥션 풀 등의 지표를 계측하고 Prometheus 형식으로 제공 |
| Prometheus | 앱 지표를 주기적으로 수집하고 시간에 따른 값 저장 |
| Grafana | Prometheus에 쿼리하여 그래프와 대시보드 표시 |

서버 배포 없이 로컬에서 실행할 수 있습니다. 앱이 실행되고 Prometheus가 앱에 접근할 수 있어야 합니다. 현재 CD는 Docker Hub에 이미지를 업로드하는 단계까지이며, 로컬 모니터링 실행과는 별개입니다.

## 2. 설정 파일 구조

```text
spring-cgv-24th/
├── build.gradle
├── Dockerfile
├── compose.yml
├── compose.monitoring.yml
├── .env                                      # Git 제외
├── monitoring/
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── grafana/
│       ├── dashboards/
│       │   └── cgv.json                       # UI 가져오기용 대시보드
│       └── provisioning/
│           └── datasources/
│               └── prometheus.yml
└── src/main/resources/
    ├── application.yaml
    └── application-monitoring.yaml
```

`application-monitoring.yaml`은 Spring Boot가 읽고, `compose.monitoring.yml`은 Docker Compose가 읽습니다. 두 파일은 서로 다른 설정 파일입니다.

## 3. Spring Boot 설정

### 3.1 의존성

[build.gradle](build.gradle)에 다음 의존성을 사용합니다. Micrometer 핵심 의존성은 Actuator를 통해 포함됩니다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

### 3.2 모니터링 프로필

[application-monitoring.yaml](src/main/resources/application-monitoring.yaml)의 내용입니다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never
  metrics:
    tags:
      application: cgv-app
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

- `health`: 앱 상태 확인 엔드포인트를 노출합니다.
- `prometheus`: Prometheus 수집용 엔드포인트를 노출합니다.
- `application`: 수집되는 앱 지표에 `application="cgv-app"` 라벨을 추가합니다.
- `percentiles-histogram`: HTTP 응답 시간의 p95 등을 계산하기 위한 히스토그램을 제공합니다.

`management`는 `spring`, `logging`과 같은 최상위 설정 이름입니다. YAML에서 항목 순서는 상관없지만 들여쓰기는 중요하며, `spring.management` 아래에 넣으면 올바른 설정 이름이 되지 않습니다.

모니터링용 Compose는 `SPRING_PROFILES_ACTIVE: prod,monitoring`을 설정합니다. 기본 `application.yaml`에 활성화된 프로필의 설정을 합쳐 사용하며, 같은 프로필 설정이 충돌하면 뒤에 지정한 프로필이 우선합니다. 파일이 없는 프로필 때문에 별도 설정 파일을 만들 필요는 없습니다.

### 3.3 인증 규칙

[SecurityConfig.java](src/main/java/com/ceos24/springboot/global/config/SecurityConfig.java)에서 다음 규칙을 `anyRequest()`보다 앞에 둡니다.

```java
.requestMatchers(
        HttpMethod.GET,
        "/actuator/health",
        "/actuator/prometheus"
).permitAll()
```

현재 JWT 필터는 토큰이 없는 요청을 다음 필터로 넘기므로 Prometheus가 JWT 없이 수집할 수 있습니다. `".actuator/prometheus"`처럼 경로 앞에 점을 쓰면 보안 설정 생성에 실패하여 앱이 시작되지 않습니다.

현재 접근 허용은 로컬 구성 기준입니다. 서버로 옮길 때는 관리 엔드포인트의 인증 또는 내부 네트워크 접근 제한을 함께 구성합니다.

## 4. Prometheus와 Grafana 연결

### 4.1 Prometheus 수집 설정

[monitoring/prometheus/prometheus.yml](monitoring/prometheus/prometheus.yml):

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: cgv-app
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - app:8080
```

`app`은 Compose 서비스 이름입니다. Prometheus가 컨테이너에서 실행되므로 앱 주소에 `localhost:8080`을 사용하지 않습니다. `job="cgv-app"` 라벨은 Prometheus가 수집 시 추가합니다.

### 4.2 Grafana 데이터 소스

[데이터 소스 provisioning 설정](monitoring/grafana/provisioning/datasources/prometheus.yml)을 통해 Grafana 시작 시 `prometheus` 데이터 소스를 자동 등록합니다.

- 접속 주소: `http://prometheus:9090`
- UID: `cg4k2w2u3da80e`
- 수집 간격 기준: `15s`
- `editable: false`: UI 대신 YAML 파일에서 데이터 소스 설정을 관리합니다.

UID는 기존 대시보드의 데이터 소스 참조를 유지하기 위해 사용합니다. 폴더 이름은 `provisioning`이며, `provising`으로 작성하면 현재 Compose 마운트 경로와 달라 자동 등록되지 않습니다.

### 4.3 Compose 구성

[compose.yml](compose.yml)은 앱과 MySQL, 외부 네트워크 `cgv-network`를 정의합니다. [compose.monitoring.yml](compose.monitoring.yml)은 로컬 앱 빌드와 포트, 모니터링 프로필, Prometheus 및 Grafana를 추가합니다.

두 파일을 `-f`로 함께 지정하면 병합되어 실행됩니다. 앱의 이미지 및 환경 변수처럼 같은 키의 값은 뒤에 지정한 파일의 설정을 따릅니다. 모니터링 서비스도 기본 네트워크인 `cgv-network`에 연결됩니다.

브라우저에서는 `localhost`를 사용하고 컨테이너 사이에서는 서비스 이름을 사용합니다. 호스트 포트는 `127.0.0.1`에 바인딩되어 있습니다.

## 5. 로컬 실행

### 5.1 준비

Java 21 및 Docker Desktop의 Linux 컨테이너 실행 환경을 준비합니다. 프로젝트 루트의 `.env`에 아래 항목을 설정합니다.

```dotenv
APP_IMAGE=cgv-app:local
DB_NAME=cgv_local
DB_URL=jdbc:mysql://db:3306/cgv_local
DB_USERNAME=cgv
DB_PASSWORD=<로컬 DB 비밀번호>
MYSQL_ROOT_PASSWORD=<로컬 root 비밀번호>
JPA_DDL_AUTO=update
JWT_SECRET=<프로젝트 JWT 서명 요건에 맞는 키>
TOSS_SECRET_KEY=<토스 테스트 시크릿 키>
GRAFANA_ADMIN_PASSWORD=<Grafana 관리자 비밀번호>
```

기존 `.env`가 있다면 기존 DB 계정과 키를 유지하고 누락된 항목만 추가합니다. 기존 MySQL 볼륨의 계정은 `.env` 수정만으로 변경되지 않습니다. 모니터링용 Compose는 앱의 DB 주소를 `jdbc:mysql://db:3306/${DB_NAME}`으로 덮어씁니다.

`.env`에는 비밀번호와 키가 있으므로 커밋하지 않습니다.

### 5.2 최초 실행 및 앱 변경 반영

프로젝트 루트의 PowerShell에서 실행합니다.

```powershell
# 네트워크가 없는 경우에만 생성
docker network inspect cgv-network *> $null
if ($LASTEXITCODE -ne 0) { docker network create cgv-network }

# Dockerfile에서 복사할 JAR 빌드
.\gradlew.bat clean build
if ($LASTEXITCODE -ne 0) { throw 'Gradle 빌드에 실패했습니다.' }

docker compose -f compose.yml -f compose.monitoring.yml up -d --build
docker compose -f compose.yml -f compose.monitoring.yml ps
```

앱도 Docker에서 실행하므로 IntelliJ에서 따로 실행하지 않습니다. Java 소스, 의존성 또는 Spring 설정을 수정하면 JAR와 앱 이미지를 다시 빌드합니다. `docker compose --build`만으로 Gradle 빌드가 실행되지는 않습니다.

앱 변경이 없고 기존 컨테이너를 다시 실행할 때:

```powershell
docker compose -f compose.yml -f compose.monitoring.yml up -d
```

### 5.3 정상 동작 확인

| 순서 | 주소 | 성공 기준 |
| --- | --- | --- |
| 1 | http://localhost:8080/actuator/health | `status`가 `UP` |
| 2 | http://localhost:8080/actuator/prometheus | 텍스트 지표 출력 |
| 3 | http://localhost:9090/targets | `cgv-app` 대상이 `UP` |
| 4 | http://localhost:3000 | Grafana 로그인 가능 |

Grafana 계정은 `admin`, 비밀번호는 초기 설정한 `GRAFANA_ADMIN_PASSWORD`입니다. 기존 Grafana 볼륨이 있으면 `.env` 변경만으로 관리자 비밀번호가 바뀌지는 않습니다.

Grafana의 **Explore**에서 `prometheus` 데이터 소스를 선택하고 다음 쿼리를 실행합니다.

```promql
up{job="cgv-app"}
```

결과 `1`은 Prometheus가 앱 지표를 성공적으로 수집했다는 뜻입니다. 모든 API나 외부 결제 서비스의 정상 동작까지 보장하지는 않습니다.

## 6. 대시보드와 그래프 확인

현재 저장한 대시보드 이름은 `cgv`입니다. 기본 조회 범위는 최근 15분, 자동 새로고침은 15초입니다. 패널 시각화 유형은 `Time series`를 사용합니다.

### 6.1 JVM 힙 메모리 사용량

```promql
sum(jvm_memory_used_bytes{job="cgv-app",area="heap"})
```

표시 단위는 `bytes (IEC)`입니다. JVM 힙 영역에서 현재 사용하는 메모리 합계이며 컨테이너 전체 메모리 사용량과는 다릅니다.

### 6.2 API별 초당 요청 수

```promql
sum by (uri) (
  rate(http_server_requests_seconds_count{
    job="cgv-app",
    uri=~"/api/.*"
  }[$__rate_interval])
)
```

단위는 `requests/sec`, 범례는 `{{uri}}`입니다. `/api/` 경로에 대해 조회 구간의 초당 요청 수를 계산합니다. `/actuator/` 수집 요청은 제외합니다. `$__rate_interval`은 Grafana가 계산하는 구간 변수이므로 Prometheus UI에서 직접 실행할 때는 `[1m]`과 같은 구간으로 바꿉니다.

### 6.3 API별 p95 응답 시간

```promql
histogram_quantile(
  0.95,
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket{
      job="cgv-app",
      uri=~"/api/.*"
    }[$__rate_interval])
  )
)
```

단위는 `seconds (s)`, 범례는 `{{uri}}`입니다. 해당 구간 요청의 약 95%가 완료되는 시간을 히스토그램으로 추정합니다. 요청이 적으면 값의 변동이 크고, 요청이 없으면 값이 표시되지 않을 수 있습니다.

### 6.4 API 호출로 확인

```powershell
1..30 | ForEach-Object {
    curl.exe -s -o NUL http://localhost:8080/api/movies
    Start-Sleep -Seconds 1
}
```

약 30~60초 후 그래프를 확인합니다. HTTP 지표는 요청 후 생성되며 `rate()` 계산에는 여러 번의 수집이 필요합니다. 인증 단계에서 차단된 요청은 URI가 `UNKNOWN`으로 기록될 수 있어 `/api/` 필터에서 제외됩니다.

## 7. 대시보드 저장과 JSON 내보내기의 차이

**대시보드를 매번 `cgv.json`으로 내보낼 필요는 없습니다.** 일상적으로는 Grafana에서 **Save**하면 됩니다. 패널, 쿼리, 레이아웃 등 대시보드 구성을 바꾸고 그 변경을 저장소에도 보관하려는 시점에 JSON을 갱신합니다.

| 저장 대상 | 저장 위치 | 저장 시점 | 내용 |
| --- | --- | --- | --- |
| Grafana 대시보드 | `grafana-data` 볼륨 | UI에서 Save | 패널, 쿼리, 레이아웃, 시간 범위 등 |
| 수집 지표 이력 | `prometheus-data` 볼륨 | Prometheus가 자동 수집 | 시간별 메모리, 요청량, 응답 시간 등의 값 |
| 대시보드 JSON | 저장소의 `monitoring/grafana/dashboards/cgv.json` | 구성 변경을 저장소에 반영할 때 | 다른 환경에서 가져올 수 있는 대시보드 정의 |

API 요청으로 그래프가 변하거나 새 지표가 수집될 때는 JSON을 다시 내보내지 않습니다. Grafana의 Save는 저장소의 JSON 파일을 자동으로 갱신하지 않으며, JSON에는 실제 지표 이력이나 Grafana 관리자 비밀번호가 포함되지 않습니다.

### 7.1 구성 변경 후 관리 순서

1. Grafana에서 패널, 쿼리, 단위 등을 편집합니다.
2. **Save**를 눌러 Grafana에 저장합니다.
3. 저장소에도 반영할 변경이면 **Export**에서 외부 공유용 내보내기 옵션(**Export for sharing externally**)을 사용합니다.
4. 내려받은 JSON으로 [cgv.json](monitoring/grafana/dashboards/cgv.json)을 갱신합니다.
5. JSON 변경을 확인한 뒤 관련 설정 및 문서와 함께 커밋합니다.

현재 내보낸 JSON의 `DS_PROMETHEUS`는 가져올 환경에서 데이터 소스를 선택하기 위한 입력입니다. 화면의 메뉴 이름은 Grafana 버전에 따라 조금 다를 수 있습니다.

### 7.2 다른 환경에서 가져오기

1. Compose를 실행하고 Prometheus 데이터 소스 연결을 확인합니다.
2. Grafana에서 **Dashboards → New → Import**를 선택합니다.
3. `monitoring/grafana/dashboards/cgv.json`을 업로드합니다.
4. 데이터 소스 입력에서 `prometheus`를 선택하고 가져옵니다.
5. 같은 UID의 대시보드가 있으면 기존 구성을 덮어쓸지 확인합니다. 별도 사본이 필요하면 가져오기 화면에서 UID를 변경합니다.

현재 JSON은 **UI 가져오기용**입니다. Compose를 실행할 때 데이터 소스는 자동 등록되지만 대시보드 JSON은 자동 등록되지 않습니다. 기존 Grafana 볼륨에 대시보드가 저장돼 있다면 매번 가져올 필요도 없습니다.

JSON은 대시보드 정의의 백업이며 전체 데이터 백업은 아닙니다. 지표 이력이나 Grafana의 다른 설정까지 복원하려면 각 데이터 볼륨의 별도 백업이 필요합니다.

## 8. 실행 중 관리

### 8.1 컨테이너 상태와 로그

```powershell
docker compose -f compose.yml -f compose.monitoring.yml ps -a
docker compose -f compose.yml -f compose.monitoring.yml logs --tail 100 app
docker compose -f compose.yml -f compose.monitoring.yml logs --tail 100 prometheus
docker compose -f compose.yml -f compose.monitoring.yml logs --tail 100 grafana
```

컨테이너의 `Up` 표시는 앱 기동 완료를 보장하지 않습니다. 앱 로그에 `Started Application`이 표시되고 health 요청에 응답하는지 함께 확인합니다. 재시작을 반복하는 경우 최근 앱 로그의 첫 원인 예외를 확인합니다.

### 8.2 수집 설정 및 데이터 소스 변경

Prometheus 수집 설정을 수정한 뒤에는 서비스를 다시 생성해 설정을 적용합니다.

```powershell
docker compose -f compose.yml -f compose.monitoring.yml up -d --force-recreate prometheus
```

Grafana 데이터 소스 YAML을 수정한 뒤에는 서비스를 다시 생성하여 provisioning을 적용합니다.

```powershell
docker compose -f compose.yml -f compose.monitoring.yml up -d --force-recreate grafana
```

각 명령은 기존 데이터 볼륨을 유지합니다. 적용 후 Prometheus의 Targets와 Grafana의 Explore를 다시 확인합니다. 앱의 Spring 설정 변경은 JAR와 앱 이미지 재빌드가 필요합니다.

### 8.3 종료 및 데이터 유지

```powershell
docker compose -f compose.yml -f compose.monitoring.yml down
```

일반 `down`은 컨테이너와 Compose가 관리하는 네트워크를 정리하지만 데이터 볼륨을 삭제하지 않습니다. 이 프로젝트의 `cgv-network`는 외부 네트워크이므로 유지됩니다. 같은 Compose 프로젝트 이름과 볼륨을 사용해 다시 실행하면 기존 데이터를 사용합니다.

`down -v`는 MySQL 데이터, Prometheus 이력, Grafana 설정을 담은 볼륨까지 삭제하므로 일반 종료에는 사용하지 않습니다. 볼륨은 Docker 호스트의 로컬 저장소이며 PC나 Docker 데이터가 손실되면 함께 잃을 수 있습니다.

## 9. 문제 해결

| 증상 | 확인 및 조치 |
| --- | --- |
| `ERR_EMPTY_RESPONSE` | 앱 기동 중이거나 실패했는지 앱 로그 확인. JAR와 이미지가 최신인지 확인 |
| health 접속 실패 | 경로가 `/actuator/health`인지 확인. `/acturator/health`는 오타 |
| `401` 또는 `403` | Actuator GET 요청 허용 규칙이 `anyRequest()`보다 앞에 있는지 확인 |
| `pattern must start with a /` | 보안 경로의 시작 문자가 `/`인지 확인. `.actuator/prometheus` 수정 후 재빌드 |
| Prometheus 대상 `DOWN` | Targets의 오류 확인. `app:8080`, 앱 기동 상태, 공통 네트워크 연결 확인 |
| Grafana 데이터 소스 미등록 | `provisioning/datasources` 경로와 Compose 마운트 확인 후 Grafana 재생성 |
| Grafana 연결 실패 | 데이터 소스 주소가 `http://prometheus:9090`인지 확인 |
| `No visualization configured` | 패널에서 Time series와 Prometheus 데이터 소스 선택 |
| `No data` | 시간 범위, API 요청 발생 여부, job 및 URI 라벨 필터, 수집 상태 확인 |
| p95에 값이 없음 | 히스토그램 설정 반영 여부와 해당 구간의 API 요청 여부 확인 |
| Grafana 로그인 실패 | 기존 볼륨의 비밀번호인지 확인. `.env` 수정만으로 기존 비밀번호는 바뀌지 않음 |
| 재실행 후 대시보드가 안 보임 | 같은 Compose 프로젝트와 Grafana 볼륨인지 확인. 새 환경이면 JSON 가져오기 |

## 10. 현재 범위와 확장

현재 구성은 로컬 앱의 메트릭 수집 및 시각화를 제공합니다. MySQL 컨테이너 자체의 상세 지표, 로그 검색, 장애 알림 및 결제 전용 비즈니스 지표는 별도 구성하지 않았습니다. 앱의 커넥션 풀 지표와 MySQL 서버 자체의 성능 지표는 구분해야 합니다.

필요에 따라 다음을 추가할 수 있습니다.

- API 오류율 및 DB 커넥션 풀 패널
- 결제 실패, 미확정 결제, 복구 재시도 횟수 등 Micrometer 사용자 정의 지표
- 장애 조건에 따른 Grafana 알림과 알림 수신 채널
- Loki 등을 이용한 로그 수집과 조회

서버 배포 시에는 관리 엔드포인트와 Grafana의 접근 제어, 데이터 저장 및 보존 정책, 실행 환경에 맞는 이미지 버전 고정을 함께 검토합니다. 현재 Compose의 Prometheus와 Grafana 이미지는 `latest` 태그를 사용합니다.

## 참고 자료

- [Spring Boot Actuator 엔드포인트](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot 메트릭](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus 쿼리 함수](https://prometheus.io/docs/prometheus/latest/querying/functions/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
