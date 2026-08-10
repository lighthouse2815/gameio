FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace/backend

COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 --create-home gameio
WORKDIR /app
COPY --from=build /workspace/backend/target/gameio-backend-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
