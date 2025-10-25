# ----------------------
# Build aşaması
# ----------------------
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Kodları kopyala
COPY pom.xml .
COPY src /app/src

# Testleri atlayarak jar build et
RUN mvn clean package -DskipTests -U

# ----------------------
# Final aşaması
# ----------------------
FROM openjdk:21-slim
WORKDIR /app

# Build aşamasındaki jar'ı kopyala
COPY --from=build /app/target/*.jar app.jar

# Container çalıştığında jar'ı çalıştır
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
