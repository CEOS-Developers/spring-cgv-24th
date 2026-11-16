
# 1. 빌드 단계
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Gradle 관련 파일 복사
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle* settings.gradle* ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# Spring Boot 소스 복사
COPY src/ src/

# 실행 가능한 JAR 생성
RUN ./gradlew bootJar --no-daemon

# 2. 실행 단계
FROM eclipse-temurin:21-jre

WORKDIR /app

# 빌드 단계에서 만든 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
