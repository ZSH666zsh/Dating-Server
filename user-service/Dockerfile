# user-service 镜像构建
# 两阶段：Stage1 用带 Maven 的大镜像编译出 jar；Stage2 换成精简 JRE 镜像瘦身

# Stage 1: 编译阶段
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
# 容器内工作目录
WORKDIR /build

# 从宿主机额外上下文拷入 5 个 proto jar（proto 包是本地 mvn install 的，容器里没有）
COPY --from=proto-libs /*.jar /tmp/proto-libs/
# 把 proto jar 逐个装进容器的 Maven 本地仓库（user-service 实际只需 user-proto，全装更省心）
RUN for jar in /tmp/proto-libs/*.jar; do \
      name=$(basename "$jar" .jar); \
      svc=${name%-proto-0.1.0-SNAPSHOT}; \
      mvn install:install-file -Dfile="$jar" \
        -DgroupId=com.dating.zhaoshihang.proto -DartifactId="${svc}-proto" \
        -Dversion=0.1.0-SNAPSHOT -Dpackaging=jar -B -ntp; \
    done

# 先拷 pom 文件（触发依赖层缓存：改代码不重下依赖）
COPY pom.xml .
# 预下载全部依赖到容器缓存（首次较慢；|| true 容忍失败）
RUN mvn dependency:go-offline -B || true
# 再拷源码（pom 没变时这步走缓存）
COPY src ./src
# 编译 + 打包成可执行 jar（跳测试，加快构建）
RUN mvn clean package -DskipTests -B

# Stage 2: 运行阶段（精简 JRE 镜像，不带 Maven）
FROM eclipse-temurin:21-jre-alpine
# 运行目录
WORKDIR /app
# 只从 Stage1 拷最终的 jar
COPY --from=builder /build/target/*.jar app.jar
# 对外端口：8081 HTTP / 9091 gRPC
EXPOSE 8081 9091
# 时区 UTC（项目红线要求）
ENV TZ=UTC
# 容器启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
