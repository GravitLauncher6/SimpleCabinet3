# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/opt/gradle

COPY --chmod=755 gradlew ./
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY modules ./modules
COPY src ./src

RUN --mount=type=cache,target=/opt/gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 cabinet \
    && useradd --uid 10001 --gid cabinet --no-create-home cabinet \
    && install -d -o cabinet -g cabinet /app/data /app/modules

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY --chmod=755 scripts/simplecabinet /usr/local/bin/simplecabinet

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["simplecabinet"]
