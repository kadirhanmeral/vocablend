# Base image olarak OpenJDK 24 kullanıyoruz
FROM openjdk:24-jdk-slim

# Jar dosyamızın yolunu argüman olarak alıyoruz
ARG JAR_FILE=target/vocablend-be-0.0.1-SNAPSHOT.jar

# Jar dosyasını container içine kopyalıyoruz
COPY ${JAR_FILE} app.jar

# Container çalıştırıldığında çalışacak komut
ENTRYPOINT ["java", "-jar", "/app.jar"]
