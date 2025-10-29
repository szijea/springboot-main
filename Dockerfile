# 基础镜像
FROM openjdk:17-jdk-slim

# 工作目录
WORKDIR /app

# 复制打包后的jar文件（假设使用Maven打包）
COPY target/pharmacy-system-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口（根据项目实际端口修改）
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]