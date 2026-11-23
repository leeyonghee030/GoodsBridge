# 배포용 이미지: 빌드(JDK) → 실행(JRE) 2단계. x86·ARM(Oracle Ampere) 모두에서 빌드된다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows 에서 받은 gradlew 의 줄바꿈(CRLF)이 리눅스에서 실행을 막지 않게 한다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
# 상품 이미지는 사용자 홈의 good_bridge_uploads 에 저장된다 (compose 에서 볼륨으로 보존)
RUN mkdir -p /home/app/good_bridge_uploads && chown -R app:app /home/app /app
USER app
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
