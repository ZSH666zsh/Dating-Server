# 多阶段构建
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8084
ENV SPRING_THREADS_VIRTUAL_ENABLED=true
ENTRYPOINT ["java", "-jar", "app.jar"]
