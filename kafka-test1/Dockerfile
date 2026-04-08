# ── 1단계: Gradle 빌드 ──────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY kafka-test1/ .
RUN chmod +x gradlew && ./gradlew bootJar -x test

# ── 2단계: 실행 이미지 ──────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Instana Java agent 다운로드
RUN apt-get update && apt-get install -y curl && \
    curl -L -o /app/instana-agent.jar \
    "https://artifact-public.instana.io/artifactory/shared/com/instana/standalone-collector-jvm/LATEST/standalone-collector-jvm-LATEST.jar" && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["java", "-javaagent:/app/instana-agent.jar", "-jar", "app.jar"]
