package com.example.football.enums;

import lombok.Getter;

@Getter
public enum BookingStatus {
    PENDING_PAYMENT("Giữ chỗ 5p - Chờ TT 100%", "bg-warning text-dark"),
    CONFIRMED("Đã xác nhận (Đã trả 100%)", "bg-success"),
    COMPLETED("Hoàn thành ca đá", "bg-primary"),
    EXPIRED_UNPAID("Hết hạn 5p (Nhả sân +1 Strike)", "bg-secondary"),
    CANCELLED_NO_REFUND("Khách hủy (Không hoàn tiền)", "bg-danger"),
    NO_SHOW("Khách bỏ sân (Không hoàn tiền)", "bg-dark");

    private final String displayName;
    private final String badgeClass;

    BookingStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}
