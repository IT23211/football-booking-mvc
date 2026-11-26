package com.example.football.entity;

import com.example.football.enums.DiscountType;
import com.example.football.enums.PromotionDisplayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "promotions", indexes = {
        @Index(name = "idx_promo_owner", columnList = "owner_id"),
        @Index(name = "idx_promo_code", columnList = "code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

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

    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Builder.Default
    @Column(name = "reserved_count", nullable = false)
    private int reservedCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean stopped = false;

    @Builder.Default
    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    public PromotionDisplayStatus getDisplayStatus() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (stopped) return PromotionDisplayStatus.STOPPED;
        if (now.isBefore(startAt)) return PromotionDisplayStatus.UPCOMING;
        if (now.isAfter(endAt)) return PromotionDisplayStatus.ENDED;
        return PromotionDisplayStatus.ACTIVE;
    }
}
