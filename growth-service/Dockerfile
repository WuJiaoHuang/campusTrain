FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Shanghai JAVA_OPTS=""
WORKDIR /app
COPY target/growth-service-1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8088
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]