# ----------------------
# Stage 1: Build jar
# ----------------------
FROM maven:3.9.2-eclipse-temurin-17 AS build

WORKDIR /app

# Maven bağımlılıklarını önceden cache için kopyala
COPY pom.xml .
RUN mvn dependency:go-offline

# Source kodları kopyala
COPY src ./src

# Jar build
RUN mvn clean package -DskipTests

# ----------------------
# Stage 2: Run
# ----------------------
FROM openjdk:24-jdk-slim

WORKDIR /app

# Build stage’den jar’ı al
COPY --from=build /app/target/*.jar app.jar

# Env variable yükleme (Render veya Docker run sırasında)
# Örn: docker run --env-file .env ...
ENV SPRING_APPLICATION_NAME=${SPRING_APPLICATION_NAME}
ENV SPRING_DATA_MONGODB_URI=${SPRING_DATA_MONGODB_URI}
ENV SPRING_DATA_MONGODB_DATABASE=${SPRING_DATA_MONGODB_DATABASE}
ENV GEMINI_API_KEY=${GEMINI_API_KEY}

# Uygulamayı çalıştır
ENTRYPOINT ["java", "-jar", "/app.jar"]
