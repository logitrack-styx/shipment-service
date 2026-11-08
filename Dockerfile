# Construcción multietapa con Maven y Java 25.
FROM maven:3-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q clean package -DskipTests

# Imagen de ejecución con JRE, sin herramientas de compilación.
FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home --shell /usr/sbin/nologin appuser

COPY --from=build /workspace/target/shipment-service-*-exec.jar /app/app.jar

USER appuser

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl --fail --silent http://localhost:8083/actuator/health || exit 1

ARG SOURCE_DIGEST
LABEL org.logitrack.source.sha256=$SOURCE_DIGEST

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
