FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM selenium/standalone-chrome:latest
USER root
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/selenium-tool-0.0.1-SNAPSHOT.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

RUN mkdir -p /app/data

EXPOSE 7900 8080

CMD ["/app/entrypoint.sh"]
