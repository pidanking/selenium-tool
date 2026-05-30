#!/bin/bash
# 启动 Selenium 底层服务（后台）
/opt/bin/entry_point.sh >/tmp/selenium-base.log 2>&1 &
# 等待 Selenium 服务就绪
sleep 3
# 启动 Java 应用（前台）
exec java $JAVA_OPTS -jar /app/app.jar
