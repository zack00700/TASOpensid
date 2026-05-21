# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# 1) Copy just pom first to allow dependency layer caching
COPY pom.xml ./

# (optional but speeds up builds by prefetching deps)
RUN mvn -B -q -DskipTests dependency:go-offline

# 2) Now copy sources
COPY src ./src

# 3) Build an uber-jar
RUN mvn -B -DskipTests -Dquarkus.package.type=uber-jar clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the fat jar built above
COPY --from=build /src/target/*-runner.jar /app/app.jar
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=${PORT:-8080} -jar /app/app.jar"]
