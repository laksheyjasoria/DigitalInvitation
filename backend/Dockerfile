FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring && useradd --system --gid spring --home-dir /app spring
COPY --from=build /workspace/target/digital-invitation-0.1.0-SNAPSHOT.jar /app/app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
