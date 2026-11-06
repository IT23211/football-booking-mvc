package com.example.football.enums;

import lombok.Getter;

@Getter
public enum Role {
    ROLE_OWNER("Chủ sân"),
    ROLE_CUSTOMER("Khách hàng");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }
}
