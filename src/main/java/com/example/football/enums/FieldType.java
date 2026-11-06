package com.example.football.enums;

import lombok.Getter;

@Getter
public enum FieldType {
    SAN_5("Sân 5 người"),
    SAN_7("Sân 7 người"),
    SAN_11("Sân 11 người");

    private final String displayName;

    FieldType(String displayName) {
        this.displayName = displayName;
    }
}
