# ---- Stage 1: build the jar ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Copy only the files needed to resolve dependencies, so this layer is cached
# until pom.xml or the Maven wrapper changes.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Source changes only invalidate the layers below.
COPY src src
RUN ./mvnw -B -DskipTests package

# ---- Stage 2: run the jar ----
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# Non-root user to run the app
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /build/target/*.jar app.jar

USER app

# Documentation only; the app listens on the PORT env var (default 8080).
EXPOSE 8080

# DB_URL, DB_USER and DB_PASSWORD must be provided as environment variables at runtime.
#
# -XX:MaxRAMPercentage=65  caps the heap at 65% of container RAM, leaving room for metaspace, threads and native memory.
# -XX:+UseSerialGC         uses the single-threaded GC, which has the lowest memory overhead for small containers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+UseSerialGC", "-jar", "app.jar"]
