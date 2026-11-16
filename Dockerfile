FROM eclipse-temurin:21-jre-alpine
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata
RUN addgroup -S spring && adduser -S spring -G spring
COPY build/libs/*.jar app.jar
USER spring:spring
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]