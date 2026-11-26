package com.example.football.form;

import com.example.football.enums.DiscountType;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PromotionForm {
    private Long id;
    private String code;
    private String name;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @Builder.Default
    private List<String> applicableDays = new ArrayList<>();

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime discountStartTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime discountEndTime;

    private Integer maxTotalUses;
    private Integer maxUsesPerCustomer;
    private String currentPassword;
}
