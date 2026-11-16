FROM eclipse-temurin:21-jre-alpine

# Java 프로세스의 권한을 제한할 전용 시스템 계정 생성
RUN addgroup -S appgroup && adduser -S -G appgroup appuser

WORKDIR /app

# Gradle bootJar가 만든 실행용 JAR만 복사
COPY build/libs/spring-cgv-24th-0.0.1-SNAPSHOT.jar app.jar

USER appuser:appgroup

# 호스트 포트는 Compose에서 매핑
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
