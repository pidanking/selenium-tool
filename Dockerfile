FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先复制 pom.xml 利用 Docker 缓存，依赖不变时不重新下载
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM selenium/standalone-chrome:latest

USER root
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

COPY --from=build /build/target/selenium-tool-0.0.1-SNAPSHOT.jar /app/app.jar

# 数据目录，用于挂载持久化文件
RUN mkdir -p /app/data

EXPOSE 7900 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8080/api/cookies || exit 1

ENTRYPOINT ["/bin/bash", "-lc", "/opt/bin/entry_point.sh >/tmp/selenium-base.log 2>&1 & exec java $JAVA_OPTS -jar /app/app.jar"]
