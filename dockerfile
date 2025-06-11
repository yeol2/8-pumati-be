# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# 캐시 최적화를 위한 Gradle 관련 파일 복사
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# 애플리케이션 소스 복사
COPY src ./src

# 빌드 실행 (테스트 제외)
RUN gradle build -x test

# 2단계: 런타임 스테이지
FROM eclipse-temurin:17-jre-alpine

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
