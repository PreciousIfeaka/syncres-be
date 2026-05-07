# ---- Dependencies stage ----
FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve dependency:resolve-plugins -B

# ---- Build stage ----
FROM dependencies AS build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 9002
EXPOSE 9003

ENTRYPOINT ["java", "-jar", "app.jar"]