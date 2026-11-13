package com.example.football;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@Slf4j
public class FootballBookingApplication {

    public static void main(String[] args) {
        configureEnvironmentWithFallback();
        SpringApplication.run(FootballBookingApplication.class, args);
    }

    /**
     * Tự động nhận diện môi trường chạy:
     * - Trên Railway.app: Kết nối trực tiếp vào mysql.railway.internal:3306/railway với tài khoản Railway.
     * - Trên máy Local (khi mysql.railway.internal không phân giải được DNS và không đặt biến môi trường):
     *   Tự động chuyển sang kết nối XAMPP / MySQL Local (localhost:3306/football_booking_db).
     */
    private static void configureEnvironmentWithFallback() {
        String explicitUrl = System.getenv("SPRING_DATASOURCE_URL");
        String explicitHost = System.getenv("MYSQLHOST");
        String railwayEnv = System.getenv("RAILWAY_ENVIRONMENT");

        if ((explicitUrl != null && !explicitUrl.isBlank())
                || (explicitHost != null && !explicitHost.isBlank())
                || (railwayEnv != null && !railwayEnv.isBlank())) {
            return;
        }

        try {
            InetAddress.getByName("mysql.railway.internal");
            log.info("[DB ENV] Phát hiện mạng nội bộ Railway (mysql.railway.internal). Sử dụng cấu hình Railway MySQL.");
        } catch (Exception ignored) {
            if (System.getProperty("MYSQLHOST") == null) {
                System.setProperty("MYSQLHOST", "localhost");
            }
            if (System.getProperty("MYSQLPORT") == null) {
                System.setProperty("MYSQLPORT", "3306");
            }
            if (System.getProperty("MYSQLDATABASE") == null) {
                System.setProperty("MYSQLDATABASE", "football_booking_db");
            }
            if (System.getProperty("MYSQLUSER") == null) {
                System.setProperty("MYSQLUSER", "root");
            }
            if (System.getProperty("MYSQLPASSWORD") == null) {
                System.setProperty("MYSQLPASSWORD", "");
            }
            log.info("[DB ENV] Đang chạy trên máy Local -> Tự động dùng localhost:3306/football_booking_db.");
        }
    }
}
