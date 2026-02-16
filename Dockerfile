FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="quantum-team"
LABEL description="Quantum Chunk Processor - Distributed file chunk worker"

RUN addgroup -S quantum && adduser -S quantum -G quantum

WORKDIR /app

COPY target/quantum-chunk-processor-*.jar app.jar

USER quantum

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
