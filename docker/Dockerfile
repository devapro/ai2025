# Multi-stage build for optimal image size

# Stage 1: Build the application
FROM gradle:8.7-jdk21 AS builder

WORKDIR /app

# Copy Gradle files first for better caching
COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY build.gradle.kts* ./

# Copy buildSrc
COPY buildSrc buildSrc

# Copy source code
COPY app app
COPY utils utils

# Build the application
RUN ./gradlew build --no-daemon

# Stage 2: Create runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create directories for prompts and history
RUN mkdir -p /app/promts /app/history

# Copy the built application
COPY --from=builder /app/app/build/libs/*.jar /app/app.jar

# Copy prompts directory
COPY promts /app/promts

# Environment variables (will be overridden by docker-compose or runtime)
ENV OPENAI_API_KEY=""
ENV TELEGRAM_BOT_TOKEN=""
ENV PROMPTS_DIR="/app/promts"
ENV HISTORY_DIR="/app/history"

# Run the application
CMD ["java", "-jar", "/app/app.jar"]
