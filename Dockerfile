# syntax=docker/dockerfile:1

# ---- build stage: compile inside the image so no local JDK is needed ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# The cache mount keeps Gradle's downloaded dependencies between builds, so only
# the first build pays for resolving them.
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon clean bootJar

# ---- runtime stage: JRE only, no Gradle, no source ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Don't run as root.
RUN useradd --system --create-home --shell /usr/sbin/nologin spring
USER spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
