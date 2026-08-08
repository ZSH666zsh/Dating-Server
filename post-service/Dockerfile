# 多阶段构建
# Stage 1: 编译
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
# 先拉依赖（缓存层）
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: 运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080 9090
ENV TZ=UTC
ENTRYPOINT ["java", "-jar", "app.jar"]
