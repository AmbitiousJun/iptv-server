FROM maven:3.5-jdk-8-alpine as builder

WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY ffmpeg ./ffmpeg
COPY maven-settings.xml /root/.m2/settings.xml

RUN mvn package -DskipTests

CMD ["java", "-jar", "/app/target/iptv-server-1.0-SNAPSHOT.jar", "--spring.profiles.active=prod"]