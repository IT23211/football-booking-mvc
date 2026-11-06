package com.example.football.enums;

import lombok.Getter;

@Getter
public enum BookingSource {
    ONLINE_CUSTOMER("Đặt Online (100% VNPay)", "bg-primary"),
    COUNTER_OWNER("Đặt tại quầy (100% Tiền mặt)", "bg-dark");

    private final String displayName;
    private final String badgeClass;

    BookingSource(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }
}

