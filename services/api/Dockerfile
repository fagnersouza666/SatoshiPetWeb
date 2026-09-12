# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

# Copia o descritor de build e o wrapper primeiro para que a resolução das
# dependências seja reutilizada independentemente de alterações no código.
COPY services/api/pom.xml services/api/mvnw ./
COPY services/api/.mvn/ ./.mvn/
RUN ./mvnw -B -ntp dependency:go-offline

COPY services/api/src/ ./src/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /deployments

COPY --from=build /workspace/target/quarkus-app/ ./

EXPOSE 8080

# Mantém o processo da aplicação sem privilégios no container.
USER 1001

ENV QUARKUS_HTTP_HOST=0.0.0.0

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
