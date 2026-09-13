# --- Build the fat jar ---
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Custom musl JRE (must match alpine runtime, not the glibc build JDK) ---
FROM eclipse-temurin:25-jdk-alpine AS jre
# java.desktop: PDFBox / ImageIO. jdk.crypto.ec: ES256 JWTs.
RUN apk add --no-cache binutils \
    && jlink \
        --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.sql,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs \
        --include-locales en,en-US \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=zip-6 \
        --output /custom-jre

# --- Runtime ---
FROM alpine:3.22
RUN apk add --no-cache fontconfig libstdc++ libgcc ca-certificates \
    && apk add --no-cache --virtual .font-src ttf-dejavu \
    && mkdir -p /usr/share/fonts/truetype \
    && cp /usr/share/fonts/dejavu/DejaVuSans.ttf /usr/share/fonts/truetype/ \
    && apk del .font-src \
    && fc-cache -f \
    && addgroup -S appuser && adduser -S -G appuser -h /app -s /sbin/nologin appuser

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre /custom-jre ${JAVA_HOME}
COPY --from=build --chown=appuser:appuser \
    /workspace/build/libs/PDF-Master-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser
WORKDIR /app
EXPOSE 8080

ENTRYPOINT ["/opt/java/bin/java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
