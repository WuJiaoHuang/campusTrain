FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Shanghai JAVA_OPTS=""
WORKDIR /app
COPY target/auth-service-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]