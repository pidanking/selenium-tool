FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# 从 GitHub Release 下载预构建 JAR，无需本地编译
RUN curl -sL "https://github.com/pidanking/selenium-tool/releases/latest/download/selenium-tool-0.0.1-SNAPSHOT.jar" -o /app/app.jar

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
RUN mkdir -p /app/data

EXPOSE 8080

CMD ["/app/entrypoint.sh"]
