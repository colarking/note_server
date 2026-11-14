FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 note && useradd --uid 10001 --gid note --create-home note && mkdir -p /app/data/media && chown -R note:note /app
WORKDIR /app
COPY --from=builder --chown=note:note /app/target/note-server-0.1.0.jar app.jar
USER note
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
