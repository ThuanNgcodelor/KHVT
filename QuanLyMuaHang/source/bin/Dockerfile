FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B test package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core \
    && useradd --system --create-home appuser
COPY --from=build /workspace/target/quan-ly-mua-hang-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /data/files && chown -R appuser:appuser /app /data/files
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
