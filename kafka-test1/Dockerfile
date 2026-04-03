# ── 1단계: Gradle 빌드 ──────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY kafka-test1/ .
RUN chmod +x gradlew && ./gradlew bootJar -x test

# ── 2단계: 실행 이미지 ──────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
