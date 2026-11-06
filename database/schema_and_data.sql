-- ================================================================================
-- DATABASE SCHEMA & SEED DATA: HỆ THỐNG QUẢN LÝ & ĐẶT SÂN BÓNG ĐÁ (MYSQL 8.0+)
-- Quy tắc: Thanh toán 100% (Không đặt cọc) - Không hoàn tiền (FORFEITED)
--          Giữ chỗ tối đa 5 phút - Khóa chống đặt trùng bằng Generated Column
-- ================================================================================

CREATE DATABASE IF NOT EXISTS football_booking
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE football_booking;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS audit_security_logs;
DROP TABLE IF EXISTS payment_transactions;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS field_maintenance_logs;
DROP TABLE IF EXISTS time_slots;
DROP TABLE IF EXISTS football_fields;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ================================================================================
-- 1. BẢNG USERS (Tài khoản Chủ sân & Khách hàng - Xác thực Email OTP & Strike)
-- ================================================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 12 rounds',
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'Nhận mã OTP 6 số và biên lai đặt sân',
    phone VARCHAR(15) NOT NULL UNIQUE COMMENT 'Số điện thoại liên hệ Việt Nam',
    role ENUM('ROLE_OWNER', 'ROLE_CUSTOMER') NOT NULL DEFAULT 'ROLE_CUSTOMER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    strike_count INT NOT NULL DEFAULT 0 COMMENT 'Số lần giữ chỗ quá 5 phút không thanh toán (>=3 khóa tài khoản)',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempt INT NOT NULL DEFAULT 0,
    lock_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 2. BẢNG FOOTBALL_FIELDS (Thông tin Sân bóng - Giá gốc theo ca đá)
-- ================================================================================
CREATE TABLE football_fields (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    field_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'VD: SAN5-A, SAN7-A',
    name VARCHAR(100) NOT NULL,
    field_type ENUM('SAN_5', 'SAN_7', 'SAN_11') NOT NULL DEFAULT 'SAN_7',
    base_price_per_slot DECIMAL(12,2) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(255) NULL,
    status ENUM('ACTIVE', 'MAINTENANCE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_field_base_price CHECK (base_price_per_slot > 0),
    CONSTRAINT fk_field_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_fields_type_status (field_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 3. BẢNG TIME_SLOTS (Khung giờ ca đá & Phụ phí giờ vàng)
-- ================================================================================
CREATE TABLE time_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_name VARCHAR(50) NOT NULL COMMENT 'VD: Ca 1, Ca 2 - Giờ vàng',
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_peak_hour BOOLEAN NOT NULL DEFAULT FALSE,
    peak_hour_extra_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_timeslot_range UNIQUE (start_time, end_time),
    CONSTRAINT chk_timeslot_extra_fee CHECK (peak_hour_extra_fee >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 4. BẢNG FIELD_MAINTENANCE_LOGS (Lịch bảo trì khóa sân theo ngày/ca của Chủ sân)
-- ================================================================================
CREATE TABLE field_maintenance_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    field_id BIGINT NOT NULL,
    maintenance_date DATE NOT NULL,
    time_slot_id BIGINT NULL COMMENT 'NULL = Khóa cả ngày, Có ID = Khóa riêng 1 ca',
    reason VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maint_field FOREIGN KEY (field_id) REFERENCES football_fields(id) ON DELETE CASCADE,
    CONSTRAINT fk_maint_slot FOREIGN KEY (time_slot_id) REFERENCES time_slots(id) ON DELETE SET NULL,
    CONSTRAINT fk_maint_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_maint_field_date (field_id, maintenance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 5. BẢNG BOOKINGS (Đơn đặt sân - Thanh toán 100%, Không hoàn tiền, Giữ chỗ 5p)
-- ================================================================================
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(30) NOT NULL UNIQUE COMMENT 'VD: BK20261106-0001',
    user_id BIGINT NULL COMMENT 'NULL nếu khách vãng lai đặt trực tiếp tại quầy',
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(15) NOT NULL,
    customer_email VARCHAR(100) NULL,
    booking_source ENUM('ONLINE_CUSTOMER', 'COUNTER_OWNER') NOT NULL DEFAULT 'ONLINE_CUSTOMER',
    field_id BIGINT NOT NULL,
    time_slot_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    field_price DECIMAL(12,2) NOT NULL COMMENT 'Giá gốc lưu cứng tại thời điểm đặt',
    extra_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Phụ phí giờ vàng lưu cứng lúc đặt',
    total_price DECIMAL(12,2) NOT NULL COMMENT 'Tổng tiền 100% = field_price + extra_fee',
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '0 khi PENDING_PAYMENT, bằng total_price khi đã trả 100%',
    status ENUM('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED', 'EXPIRED_UNPAID', 'CANCELLED_NO_REFUND', 'NO_SHOW') NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status ENUM('UNPAID', 'PAID', 'FORFEITED') NOT NULL DEFAULT 'UNPAID',
    note VARCHAR(500) NULL,
    hold_expires_at DATETIME NOT NULL COMMENT 'Thời hạn giữ chỗ tối đa 5 phút',
    created_by_owner BIGINT NULL COMMENT 'ID Chủ sân nếu đặt tại quầy',
    cancelled_by BIGINT NULL,
    cancel_reason VARCHAR(255) NULL,
    active_slot_lock TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED') THEN 1 ELSE NULL END
    ) STORED COMMENT 'Cột ảo khóa chống đặt trùng lịch cấp độ Database',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_prevent_double_booking UNIQUE (field_id, booking_date, time_slot_id, active_slot_lock),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_booking_field FOREIGN KEY (field_id) REFERENCES football_fields(id),
    CONSTRAINT fk_booking_timeslot FOREIGN KEY (time_slot_id) REFERENCES time_slots(id),
    CONSTRAINT fk_booking_owner FOREIGN KEY (created_by_owner) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_booking_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_booking_status_hold (status, hold_expires_at),
    INDEX idx_booking_date_status (booking_date, status),
    INDEX idx_booking_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 6. BẢNG PAYMENT_TRANSACTIONS (Giao dịch thu tiền 100%)
-- ================================================================================
CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    txn_ref VARCHAR(50) NOT NULL UNIQUE COMMENT 'Mã tham chiếu gửi sang VNPay',
    provider_txn_no VARCHAR(100) NULL COMMENT 'Mã giao dịch ngân hàng trả về',
    payment_channel ENUM('VNPAY', 'VIETQR', 'CASH_AT_COUNTER') NOT NULL DEFAULT 'VNPAY',
    amount DECIMAL(12,2) NOT NULL COMMENT 'Luôn bằng 100% total_price của đơn',
    bank_code VARCHAR(30) NULL,
    response_code VARCHAR(10) NULL COMMENT '00 là thành công',
    status ENUM('PENDING', 'SUCCESS', 'FAILED', 'INVALID_SIGNATURE') NOT NULL DEFAULT 'PENDING',
    secure_hash VARCHAR(255) NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_payment_booking (booking_id),
    INDEX idx_payment_txn_ref (txn_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- 7. BẢNG AUDIT_SECURITY_LOGS (Nhật ký bảo mật & Chống phá hoại)
-- ================================================================================
CREATE TABLE audit_security_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(50) NULL,
    event_type ENUM('SLOT_HOARDING_STRIKE', 'ACCOUNT_LOCKED_STRIKES', 'UNAUTHORIZED_403', 'IDOR_ATTEMPT_BLOCKED', 'CHECKSUM_TAMPERED', 'BRUTE_FORCE_LOCK') NOT NULL,
    target_endpoint VARCHAR(255) NULL,
    ip_address VARCHAR(45) NOT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_event_created (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================================
-- SEED DATA MẪU (Mật khẩu mặc định cho tất cả tài khoản mẫu: 123456aA@)
-- Chuỗi BCrypt 12 rounds của '123456aA@': $2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW
-- ================================================================================

INSERT INTO users (id, username, password, full_name, email, phone, role, email_verified, strike_count, enabled, failed_attempt)
VALUES
(1, 'owner', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'Nguyễn Quản Lý (Chủ Sân)', 'owner@footballvn.com', '0901234567', 'ROLE_OWNER', TRUE, 0, TRUE, 0),
(2, 'khachhang1', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'Trần Văn Khách', 'khachhang1@gmail.com', '0987654321', 'ROLE_CUSTOMER', TRUE, 0, TRUE, 0),
(3, 'khachhang2', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'Lê Hoàng Nam', 'khachhang2@gmail.com', '0912345678', 'ROLE_CUSTOMER', TRUE, 1, TRUE, 0);

INSERT INTO football_fields (id, field_code, name, field_type, base_price_per_slot, description, image_url, status, created_by)
VALUES
(1, 'SAN5-A', 'Sân 5A - Cỏ Nhân Tạo FIFA Pro', 'SAN_5', 250000.00, 'Sân 5 người mặt cỏ nhân tạo đạt chuẩn FIFA Quality, hệ thống đèn LED chống chói 400W, lưới chắn bóng mới 100%.', 'https://images.unsplash.com/photo-1529900748604-07564a03e7a6?auto=format&fit=crop&w=900&q=80', 'ACTIVE', 1),
(2, 'SAN5-B', 'Sân 5B - Cỏ Nhân Tạo Cao Cấp', 'SAN_5', 250000.00, 'Sân 5 người thoáng mát, thoát nước siêu tốc khi trời mưa, hạt cao su êm chân chống chấn thương.', 'https://images.unsplash.com/photo-1575361204480-aadea25e6e68?auto=format&fit=crop&w=900&q=80', 'ACTIVE', 1),
(3, 'SAN7-A', 'Sân 7A - Tiêu Chuẩn Giải Phong Trào', 'SAN_7', 450000.00, 'Sân 7 người kích thước chuẩn 40m x 60m, có khán đài mini có mái che và phòng thay đồ riêng.', 'https://images.unsplash.com/photo-1518605368461-1ee7e53f2056?auto=format&fit=crop&w=900&q=80', 'ACTIVE', 1),
(4, 'SAN11-A', 'Sân 11A - Sân Lớn Liên Hợp Quốc Tế', 'SAN_11', 950000.00, 'Sân 11 người tiêu chuẩn thi đấu chuyên nghiệp, dàn đèn cao áp công suất lớn phục vụ đá giải ban đêm.', 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=900&q=80', 'ACTIVE', 1);

INSERT INTO time_slots (id, slot_name, start_time, end_time, is_peak_hour, peak_hour_extra_fee, is_active)
VALUES
(1, 'Ca 1 - Sáng Sớm (06:00 - 07:30)', '06:00:00', '07:30:00', FALSE, 0.00, TRUE),
(2, 'Ca 2 - Sáng (07:30 - 09:00)', '07:30:00', '09:00:00', FALSE, 0.00, TRUE),
(3, 'Ca 3 - Chiều (16:00 - 17:30)', '16:00:00', '17:30:00', FALSE, 0.00, TRUE),
(4, 'Ca 4 - Giờ Vàng 1 (17:30 - 19:00)', '17:30:00', '19:00:00', TRUE, 120000.00, TRUE),
(5, 'Ca 5 - Giờ Vàng 2 (19:00 - 20:30)', '19:00:00', '20:30:00', TRUE, 120000.00, TRUE),
(6, 'Ca 6 - Tối Muộn (20:30 - 22:00)', '20:30:00', '22:00:00', TRUE, 60000.00, TRUE);

INSERT INTO bookings (id, booking_code, user_id, customer_name, customer_phone, customer_email, booking_source, field_id, time_slot_id, booking_date, field_price, extra_fee, total_price, paid_amount, status, payment_status, note, hold_expires_at, created_by_owner)
VALUES
(1, 'BK20261106-0001', 2, 'Trần Văn Khách', '0987654321', 'khachhang1@gmail.com', 'ONLINE_CUSTOMER', 1, 4, CURDATE(), 250000.00, 120000.00, 370000.00, 370000.00, 'CONFIRMED', 'PAID', 'Đặt online thanh toán 100% qua VNPay', DATE_ADD(NOW(), INTERVAL 5 MINUTE), NULL),
(2, 'BK20261106-0002', NULL, 'Anh Tuấn (Khách đặt tại quầy)', '0933445566', NULL, 'COUNTER_OWNER', 3, 5, CURDATE(), 450000.00, 120000.00, 570000.00, 570000.00, 'CONFIRMED', 'PAID', 'Thu 100% tiền mặt tại quầy', DATE_ADD(NOW(), INTERVAL 5 MINUTE), 1),
(3, 'BK20261106-0003', 3, 'Lê Hoàng Nam', '0912345678', 'khachhang2@gmail.com', 'ONLINE_CUSTOMER', 2, 4, CURDATE(), 250000.00, 120000.00, 370000.00, 370000.00, 'CANCELLED_NO_REFUND', 'FORFEITED', 'Khách hủy đơn sát giờ - Không hoàn tiền, mở lại ca', DATE_ADD(NOW(), INTERVAL 5 MINUTE), NULL);

INSERT INTO payment_transactions (id, booking_id, txn_ref, provider_txn_no, payment_channel, amount, bank_code, response_code, status, paid_at)
VALUES
(1, 1, 'BK20261106-0001-T1', 'VNP14582910', 'VNPAY', 370000.00, 'NCB', '00', 'SUCCESS', NOW()),
(2, 2, 'BK20261106-0002-T1', 'CASH-0002', 'CASH_AT_COUNTER', 570000.00, 'CASH', '00', 'SUCCESS', NOW()),
(3, 3, 'BK20261106-0003-T1', 'VNP14582999', 'VNPAY', 370000.00, 'VCB', '00', 'SUCCESS', NOW());

