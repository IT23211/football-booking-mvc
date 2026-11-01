package com.example.football.enums;

import lombok.Getter;

@Getter
public enum FieldStatus {
    ACTIVE("Đang hoạt động", "bg-success"),
    MAINTENANCE("Đang bảo trì", "bg-warning text-dark"),
    INACTIVE("Ngưng hoạt động", "bg-secondary");

    private final String displayName;
    private final String badgeClass;

    FieldStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}

