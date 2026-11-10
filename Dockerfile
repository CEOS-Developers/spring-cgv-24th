# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 설정 파일만 먼저 복사해 소스가 바뀌어도 의존성 다운로드를 재사용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null 2>&1 || true

# 소스 복사 후 실행용 jar(bootJar) 빌드. 테스트는 CI 단계에서 수행하므로 이미지 빌드에서는 제외
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 빌드 단계의 실행 jar만 복사 (JDK, Gradle, 소스는 최종 이미지에 포함되지 않음)
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
