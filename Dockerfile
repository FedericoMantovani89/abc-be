# Stage 1: build con Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: runtime JRE slim
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S abc && adduser -S abc -G abc
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /var/data/uploads && chown abc:abc /var/data/uploads
USER abc
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
