# 빌드 도구(JDK·Gradle 캐시)는 실행 이미지에 필요 없다. 단계를 나눠 실행 이미지에는 JRE와 jar 내용만 남긴다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성은 소스보다 드물게 바뀐다. 빌드 스크립트만 먼저 복사해 받아 두면 소스만 바뀐 빌드는 이 레이어를 캐시에서 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 CI가 이미 돌린다. 동시성 테스트는 Testcontainers라 docker build 안에서는 Docker를 쓸 수 없어 실패한다.
RUN ./gradlew bootJar --no-daemon -x test

# jar 하나를 통째로 복사하면 코드 한 줄만 바뀌어도 의존성까지 포함한 레이어 전체를 다시 받아야 한다.
# 자주 안 바뀌는 의존성과 자주 바뀌는 애플리케이션 코드를 레이어로 나눈다.
RUN java -Djarmode=tools -jar build/libs/cgv.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app

# 컨테이너가 탈취돼도 root 권한을 넘겨주지 않도록 일반 사용자로 실행한다.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
ENV TZ=Asia/Seoul
EXPOSE 8080

# JVM은 컨테이너 메모리 한도를 인식하지만 기본값은 힙을 1/4만 쓴다. 컨테이너 안에는 이 프로세스뿐이라 비율을 높인다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
