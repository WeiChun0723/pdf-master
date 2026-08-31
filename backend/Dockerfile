# --- Build stage ---
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# Copy Gradle wrapper and build config first for dependency caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Copy source and build (layer invalidated only when source changes)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:25-jre-alpine

# Fonts for PDF processing
RUN apk add --no-cache fontconfig ttf-dejavu && \
    addgroup -S appuser && adduser -S -G appuser -h /app -s /sbin/nologin appuser

WORKDIR /app

COPY --from=build --chown=appuser:appuser \
    /workspace/build/libs/PDF-Master-0.0.1-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
