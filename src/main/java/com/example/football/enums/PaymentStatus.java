package com.example.football.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    UNPAID("Chưa thanh toán (0%)", "bg-secondary"),
    PAID("Đã thanh toán 100%", "bg-success"),
    FORFEITED("Mất 100% phí (Do hủy / Bỏ sân)", "bg-danger");

    private final String displayName;
    private final String badgeClass;

    PaymentStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}
