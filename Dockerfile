FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN curl -sL "https://github.com/pidanking/selenium-tool/releases/latest/download/selenium-tool-0.0.1-SNAPSHOT.jar" -o /app/app.jar

RUN mkdir -p /app/data

EXPOSE 8080

CMD java $JAVA_OPTS -jar /app/app.jar
