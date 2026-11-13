# ===================================================================
# STAGE 1: BUILD ỨNG DỤNG VỚI MAVEN & JDK 21
# ===================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml trước để tận dụng Docker Layer Cache cho thư viện Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy toàn bộ mã nguồn và đóng gói file JAR (bỏ qua unit test khi build image)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===================================================================
# STAGE 2: CHẠY ỨNG DỤNG VỚI JRE 21 NHẸ & TỐI ƯU CHO RAILWAY.APP
# ===================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Cài đặt múi giờ Việt Nam (Asia/Ho_Chi_Minh) để đồng bộ thời gian đặt sân & chữ ký VNPay
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

ENV TZ=Asia/Ho_Chi_Minh
ENV APP_UPLOAD_DIR=/app/uploads/fields

# Tạo thư mục lưu ảnh upload sân bóng trong container
RUN mkdir -p /app/uploads/fields

# Copy file JAR từ stage build sang stage runtime
COPY --from=build /app/target/*.jar app.jar

# Railway tự động cấp biến môi trường PORT khi chạy container
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT:-8080} -jar app.jar"]
