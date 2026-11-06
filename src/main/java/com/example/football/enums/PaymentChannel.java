package com.example.football.enums;

import lombok.Getter;

@Getter
public enum PaymentChannel {
    VNPAY("Cổng VNPay (100%)"),
    VIETQR("Chuyển khoản VietQR (100%)"),
    CASH_AT_COUNTER("Tiền mặt tại quầy (100%)");

    private final String displayName;

    PaymentChannel(String displayName) {
        this.displayName = displayName;
    }
}

