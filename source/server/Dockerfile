# ---- build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/caro-server-*.jar app.jar
# 8080: REST + WebSocket, 9090: raw TCP game protocol
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
