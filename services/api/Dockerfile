# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Imagem da API Quarkus — build multi-stage JVM
#
# Estágio 1 (build): Maven 3.9 + Eclipse Temurin 25 compila e empacota o Quarkus
# Estágio 2 (runtime): JRE mínimo Eclipse Temurin 25 executa o fast-jar
#
# Build context: raiz do repositório (docker compose -f infra/docker-compose.yml)
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Estágio 1 — build Maven
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# Copia o descritor e o wrapper antes do código para reutilizar o cache de
# dependências quando apenas o código-fonte muda.
COPY services/api/pom.xml services/api/mvnw ./
COPY services/api/.mvn/ ./.mvn/

# Resolve as dependências offline para acelerar rebuilds posteriores
RUN ./mvnw -B -ntp dependency:go-offline

# Copia o código-fonte e empacota (testes de unidade são executados no CI)
COPY services/api/src/ ./src/
RUN ./mvnw -B -ntp package -DskipTests

# ---------------------------------------------------------------------------
# Estágio 2 — runtime JRE
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

LABEL org.opencontainers.image.title="satoshi-pet-api" \
      org.opencontainers.image.description="Satoshi Pet Web — API Quarkus 3.33 / Java 25" \
      org.opencontainers.image.source="https://github.com/satoshi-pet/satoshi-pet-web"

WORKDIR /deployments

# Quarkus fast-jar: quarkus-run.jar + lib/ + quarkus/ + app/
COPY --from=build /workspace/target/quarkus-app/ ./

EXPOSE 8080

# Processo da aplicação sem privilégios de root no container
USER 1001

ENV QUARKUS_HTTP_HOST=0.0.0.0

# JVM otimizada para containers:
# -XX:+UseContainerSupport   — lê limites de CPU/memória do cgroup
# -XX:MaxRAMPercentage=75.0  — usa 75% da memória alocada ao container
ENTRYPOINT [ \
    "java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "/deployments/quarkus-run.jar" \
]
