package com.example.football.enums;

import lombok.Getter;

@Getter
public enum BookingStatus {
    PENDING_PAYMENT("Chờ thanh toán", "bg-warning text-dark"),
    PENDING_APPROVAL("Chờ chủ sân duyệt", "bg-info text-dark"),
    CONFIRMED("Đã xác nhận", "bg-success"),
    CANCELLED("Đã hủy", "bg-danger"),
    COMPLETED("Hoàn thành", "bg-primary");

    private final String displayName;
    private final String badgeClass;

    BookingStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}

