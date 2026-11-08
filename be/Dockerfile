# be/Dockerfile — bootJar 멀티스테이지. OCI ARM64 호환 (두 베이스 모두 multi-arch 공식).
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
COPY src src
COPY gradle.lockfile ./
# 테스트는 CI·로컬에서 이미 검증 — 이미지 빌드는 bootJar만 (lockfile 버전으로 해석됨)
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
