# syntax=docker/dockerfile:1
# expense-service - Expense Approval Workflow bounded context
#
# Multi-stage build. The build context is the REPOSITORY ROOT (see docker-compose.yml) so the
# reactor can build orgwise-common together with this module:
#   docker build -f expense-service/Dockerfile -t orgwise/expense-service .
# The module must be listed in the root pom.xml <modules>.

# ---- Stage 1: build the Spring Boot jar ------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -pl expense-service -am package \
 && cp "$(ls expense-service/target/*.jar | grep -vE '(original|plain|sources|javadoc)' | head -n 1)" /workspace/app.jar

# ---- Stage 2: slim runtime image -------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S orgwise && adduser -S -G orgwise orgwise
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER orgwise
EXPOSE 8083
ENV SERVER_PORT=8083 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 \
    CMD wget -qO- "http://localhost:8083/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
