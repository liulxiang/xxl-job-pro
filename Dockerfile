# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

# 先只拷 pom，利用镜像层缓存加速依赖下载
COPY pom.xml ./
COPY xxl-job-core/pom.xml xxl-job-core/
COPY xxl-job-admin/pom.xml xxl-job-admin/
COPY xxl-job-executor-sample-springboot/pom.xml xxl-job-executor-sample-springboot/
RUN mvn -B -ntp -q -pl xxl-job-admin -am dependency:go-offline || true

COPY xxl-job-core ./xxl-job-core
COPY xxl-job-admin ./xxl-job-admin
RUN mvn -B -ntp -pl xxl-job-admin -am package -DskipTests -Dmaven.test.skip=true \
    && find xxl-job-admin/target -maxdepth 1 -name 'xxl-job-admin-*.jar' \
       ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
       -exec cp {} /build/app.jar \;

# ---------- runtime stage ----------
FROM eclipse-temurin:8-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/app.jar /app/xxl-job-admin.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/xxl-job-admin/toLogin > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=$TZ -jar /app/xxl-job-admin.jar"]
