# apps/* 의 Spring Boot 앱을 기존 Gradle bootJar 로 빌드해 띄운다.
# 빌드 컨텍스트는 저장소 루트다. APP 에 apps/ 아래 모듈 이름을 넘긴다.
#   docker build -f docker/app.Dockerfile --build-arg APP=commerce-api .

FROM eclipse-temurin:21-jdk AS build
ARG APP
WORKDIR /workspace
COPY . .
# 이미지 빌드에서는 테스트를 돌리지 않는다. 테스트는 Gradle 태스크로 따로 돈다
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :apps:${APP}:bootJar --no-daemon -x test \
    && cp apps/${APP}/build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "/app.jar"]
