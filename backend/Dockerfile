# Backend do SGT EHS/Visitas Online (25d). Mesma base do SGT (Java 21 + Maven).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY --from=build /app/target/sgt-online-backend-*.jar /app/online.jar
EXPOSE 8090
CMD ["java", "-jar", "/app/online.jar"]
