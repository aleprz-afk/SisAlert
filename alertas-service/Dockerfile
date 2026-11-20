# syntax=docker/dockerfile:1
# alertas-service (Spring Boot, Java 17)

# ---- Compilación ----
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
# Primero solo lo necesario para bajar dependencias: si el código cambia pero el pom no,
# Docker reutiliza esta capa y no las vuelve a descargar.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# sed: si el repo se clonó en Windows, mvnw puede venir con fin de línea CRLF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Las pruebas corren en CI (GitHub Actions), no al construir la imagen.
RUN ./mvnw -B -q -DskipTests package && cp target/alertas-service-*.jar /src/app.jar

# ---- Ejecución ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S sisalert && adduser -S sisalert -G sisalert
COPY --from=build /src/app.jar app.jar
USER sisalert

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health/liveness" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
