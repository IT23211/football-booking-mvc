package com.example.football.enums;

import lombok.Getter;

@Getter
public enum SecurityEventType {
    SLOT_HOARDING_STRIKE("Cảnh báo giữ chỗ ảo quá 5 phút (+1 Strike)"),
    ACCOUNT_LOCKED_STRIKES("Khóa tài khoản do giữ chỗ ảo >= 3 lần"),
    UNAUTHORIZED_403("Cố tình truy cập trái phép khu vực /owner/**"),
    IDOR_ATTEMPT_BLOCKED("Chặn tấn công IDOR thao tác đơn người khác"),
    CHECKSUM_TAMPERED("Cảnh báo giả mạo chữ ký số VNPay HMAC-SHA512"),
    BRUTE_FORCE_LOCK("Khóa tạm thời do sai mật khẩu / OTP quá 5 lần");

    private final String description;

    SecurityEventType(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return description;
    }
}
