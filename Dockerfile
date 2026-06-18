# Use official Maven image to build the app
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

ARG MODULE=incident-service

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY discovery-server/pom.xml discovery-server/
COPY gamification-service/pom.xml gamification-service/
COPY incident-service/pom.xml incident-service/
COPY notification-service/pom.xml notification-service/
COPY user-service/pom.xml user-service/

COPY ${MODULE}/src ${MODULE}/src/
RUN mvn clean package -pl ${MODULE} -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ARG MODULE=incident-service
COPY --from=build /app/${MODULE}/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
