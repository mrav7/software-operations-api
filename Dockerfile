FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-jammy

RUN groupadd --system app && useradd --system --gid app --home-dir /app app

WORKDIR /app

COPY --from=build --chown=app:app \
    /workspace/target/software-operations-api-0.1.0-SNAPSHOT.jar \
    /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
