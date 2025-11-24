FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ARG APP_VERSION=1.0.0
# 创建非 root 用户
RUN useradd -u 10001 -m appuser
COPY --from=build /workspace/target/pharmacy-system-1.0.0.jar app.jar
EXPOSE 8080
USER appuser
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
