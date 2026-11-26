package com.example.football.enums;

import lombok.Getter;

@Getter
public enum PromotionDisplayStatus {
    STOPPED("Đã ngừng áp dụng"),
    UPCOMING("Sắp diễn ra"),
    ACTIVE("Đang áp dụng"),
    ENDED("Đã kết thúc");

    private final String displayName;

    PromotionDisplayStatus(String displayName) {
        this.displayName = displayName;
    }
}
