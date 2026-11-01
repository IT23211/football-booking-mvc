package com.example.football.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    UNPAID("Chưa thanh toán", "bg-secondary"),
    PAID("Đã thanh toán", "bg-success"),
    FAILED("Thanh toán thất bại", "bg-danger"),
    REFUNDED("Đã hoàn tiền", "bg-info text-dark");

    private final String displayName;
    private final String badgeClass;

    PaymentStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}

