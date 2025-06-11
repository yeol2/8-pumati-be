# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 소스 복사
COPY src ./src

# Gradle Wrapper 실행 권한 부여
RUN chmod +x ./gradlew

# 빌드 실행 (테스트 제외)
RUN ./gradlew build -x test --stacktrace

# 2단계: 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
