package com.example.football.entity;

import com.example.football.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "promotion_versions", uniqueConstraints = @UniqueConstraint(name = "uk_promo_version", columnNames = {"promotion_id", "version_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PromotionVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "applicable_days", nullable = false, length = 100)
    private String applicableDays;

    @Column(name = "discount_start_time", nullable = false)
    private LocalTime discountStartTime;

    @Column(name = "discount_end_time", nullable = false)
    private LocalTime discountEndTime;

    @Column(name = "max_total_uses")
    private Integer maxTotalUses;

    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "change_note", length = 500)
    private String changeNote;
}
