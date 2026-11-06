package com.example.football.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Tự động kiểm tra và phục hồi lỗi hỏng InnoDB Tablespace trên XAMPP / MariaDB / MySQL
 * (SQL Error 1932: Table '...' doesn't exist in engine) trước khi Hibernate khởi tạo EntityManagerFactory.
 */
@Configuration
@Slf4j
public class DatabaseSelfHealingConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            verifyAndRepairCorruptedTablespace(dataSource);
        }
        return bean;
    }

    private void verifyAndRepairCorruptedTablespace(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String catalog = conn.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                return;
            }

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }

            boolean corrupted = false;
            for (String table : tables) {
                try (ResultSet ignored = stmt.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
                    // Bảng hoạt động bình thường
                } catch (SQLException ex) {
                    if (ex.getErrorCode() == 1932
                            || (ex.getMessage() != null && ex.getMessage().contains("doesn't exist in engine"))) {
                        corrupted = true;
                        log.warn("[DB SELF-HEALING] Phát hiện bảng '{}' bị lỗi InnoDB Tablespace (Error 1932). Đang tự động dọn dẹp...", table);
                        try {
                            stmt.execute("DROP TABLE IF EXISTS `" + table + "`");
                        } catch (SQLException dropEx) {
                            log.warn("[DB SELF-HEALING] Không thể DROP bảng '{}': {}", table, dropEx.getMessage());
                        }
                    }
                }
            }

            if (corrupted) {
                try {
                    stmt.execute("DROP DATABASE IF EXISTS `" + catalog + "`");
                    stmt.execute("CREATE DATABASE `" + catalog + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    stmt.execute("USE `" + catalog + "`");
                    log.info("[DB SELF-HEALING] Đã tái tạo sạch sẽ Database '{}' để Hibernate dựng lại 7 bảng chuẩn.", catalog);
                } catch (SQLException recreateEx) {
                    log.warn("[DB SELF-HEALING] Cảnh báo khi tái tạo Database '{}': {}", catalog, recreateEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.debug("[DB SELF-HEALING] Bỏ qua bước kiểm tra sớm DataSource: {}", ex.getMessage());
        }
    }
}

