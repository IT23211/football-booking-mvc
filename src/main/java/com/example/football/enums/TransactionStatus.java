package com.example.football.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {
    PENDING("Đang xử lý", "bg-warning text-dark"),
    SUCCESS("Thành công (100%)", "bg-success"),
    FAILED("Thất bại", "bg-danger"),
    INVALID_SIGNATURE("Sai chữ ký HMAC-SHA512", "bg-dark");

    private final String displayName;
    private final String badgeClass;

    TransactionStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}

