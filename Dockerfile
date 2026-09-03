# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline || true

COPY src src
RUN ./mvnw -B clean package -DskipTests

RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S spring && adduser -u 1001 -S spring -G spring
WORKDIR /app

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8080

# Relies on management.endpoint.health.probes.enabled=true (application.yml) so
# /actuator/health/liveness exists outside a Kubernetes-detected environment too.
# https + --no-check-certificate: the app now serves TLS only (server.ssl.enabled),
# using a self-signed dev cert — wget would otherwise fail this check on the
# untrusted-CA chain even though the app itself is healthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --no-check-certificate --spider https://localhost:8080/actuator/health/liveness || exit 1

# -XX:+UseContainerSupport: respect the container's cgroup memory/CPU limits
#   rather than the host's, so heap sizing doesn't ignore a Kubernetes resource limit.
# -XX:MaxRAMPercentage=75.0: cap heap at 75% of the container's memory limit,
#   leaving headroom for thread stacks, metaspace, and off-heap buffers — a hard
#   OOMKill from Kubernetes is far harder to diagnose than a controlled heap ceiling.
# -XX:+UseG1GC: explicit rather than relying on JVM ergonomics to pick it.
# -Djava.security.egd=file:/dev/./urandom: this app uses SecureRandom for AES-GCM
#   IVs (PiiEncryptionConverter) and JWT signing; blocking /dev/random entropy
#   exhaustion under container load has caused real request-latency spikes in the
#   past for exactly this kind of workload — /dev/urandom is non-blocking and
#   cryptographically sufficient for this use case.
# Exec form (no shell wrapper) so SIGTERM from Kubernetes reaches the JVM
# directly as PID 1, allowing graceful shutdown within terminationGracePeriodSeconds
# instead of being ignored until a SIGKILL.
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
