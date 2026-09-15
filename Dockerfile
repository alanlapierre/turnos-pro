# Stage 1: builder. Compiles the application and extracts the layered jar.
# Build tools (JDK + Maven) are discarded once this stage finishes.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache Maven dependencies in a separate layer so that source changes do not
# trigger a full re-resolution of external dependencies.
COPY pom.xml ./
RUN mvn -B dependency:go-offline -DskipTests || true

# Compile and package the application (skipping tests; they need Docker).
COPY src/ src/
RUN mvn -B package -DskipTests

# Layered jar extraction: splits the binary into dependencies, loader and
# application layers so each one maps to an independent image layer in runtime.
RUN cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 2: runtime. Minimal JRE image, non-root user, cgroups-aware JVM.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security isolation inside the container.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /application

# Copy every extracted layer. The order matters: layers that change less often
# (dependencies) are copied first, so Docker can reuse cached image layers.
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Make the whole application directory writable by the non-root user.
RUN chown -R app:app /application

USER app

EXPOSE 8080

# JVM memory ergonomics: respect the cgroup memory limit set by Docker/Kubernetes
# instead of sizing the heap from the host. MaxRAMPercentage avoids OOMKilled
# because it leaves room for metaspace, code cache, thread stacks, etc.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0"

# --enable-preview is required at runtime because classes were compiled with Java 21 preview features.
ENTRYPOINT ["sh", "-c", "exec java --enable-preview $JAVA_OPTS -jar application.jar"]