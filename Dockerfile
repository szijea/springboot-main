FROM eclipse-temurin:17-jdk
WORKDIR /app
ARG APP_VERSION=1.0.0
ENV JAVA_OPTS=""
COPY target/pharmacy-system-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
