package com.example.football.form;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PromotionPreviewResult {
    private boolean applicable;
    private String message;
    private String code;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private Integer promotionVersion;
}
