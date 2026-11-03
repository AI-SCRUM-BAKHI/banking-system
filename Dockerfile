FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :app-bootstrap:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build /workspace/app-bootstrap/build/libs/scrum-ai-backend.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
