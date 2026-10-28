# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app && mkdir -p /app/data/uploads && chown -R app /app
WORKDIR /app
COPY --from=build /src/target/lojafacil-backend-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
