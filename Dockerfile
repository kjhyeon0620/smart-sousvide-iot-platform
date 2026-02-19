# syntax=docker/dockerfile:1.7

  FROM eclipse-temurin:17-jdk-jammy AS builder
  WORKDIR /workspace

  # Gradle wrapper + build scripts
  COPY gradlew gradlew
  COPY gradle gradle
  COPY build.gradle settings.gradle ./
  RUN chmod +x gradlew

  # Source
  COPY src src

  # Build executable Spring Boot jar
  RUN --mount=type=cache,target=/root/.gradle \
      ./gradlew --no-daemon clean bootJar -x test

  FROM eclipse-temurin:17-jre-jammy AS runtime
  WORKDIR /app

  # Non-root user
  RUN groupadd -r spring && useradd -r -g spring spring

  # JVM defaults (override at runtime with -e JAVA_OPTS=...)
  ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

  # Copy built jar
  COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

  USER spring:spring
  EXPOSE 8080

  ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
