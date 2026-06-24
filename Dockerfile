FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
