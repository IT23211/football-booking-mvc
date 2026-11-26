package com.example.football.enums;

import lombok.Getter;

@Getter
public enum DiscountType {
    PERCENTAGE("Giảm theo phần trăm"),
    FIXED_AMOUNT("Giảm số tiền cố định");

    private final String displayName;

    DiscountType(String displayName) {
        this.displayName = displayName;
    }
}
