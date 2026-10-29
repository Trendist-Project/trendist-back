# Java 25 실행 환경(빌드는 GitHub Actions에서 끝내고 jar만 넣는다)
FROM eclipse-temurin:25-jre

WORKDIR /app

# JAR 파일 복사(./gradlew bootJar 결과물)
COPY build/libs/trendist-back-0.1.0.jar trendist-back.jar

EXPOSE 4001

# 애플리케이션 실행
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "trendist-back.jar"]
