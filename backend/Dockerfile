# 階段 1: 建置環境
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
# 下載依賴快取
RUN mvn dependency:go-offline -B
COPY src ./src
# 編譯並包裝為 JAR
RUN mvn clean package -DskipTests

# 階段 2: 運作環境
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/eip-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
