FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# Production bind-mounts host /opt (project .env trees). Keep the JRE outside that path
# so the mount cannot hide java from PATH.
RUN mv /opt/java /usr/lib/jvm/temurin-21
ENV JAVA_HOME=/usr/lib/jvm/temurin-21
ENV PATH="${JAVA_HOME}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin nexus
COPY --from=build --chown=nexus:nexus /app/target/nexus-backend-0.0.1-SNAPSHOT.jar app.jar
USER nexus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
