package com.example.football.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_reservations", uniqueConstraints = @UniqueConstraint(
        name = "uk_promo_reservation_booking", columnNames = "booking_id"), indexes = {
        @Index(name = "idx_promo_res_promo", columnList = "promotion_id,status"),
        @Index(name = "idx_promo_res_user", columnList = "user_id,status"),
        @Index(name = "idx_promo_res_booking", columnList = "booking_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PromotionReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "promotion_version_number", nullable = false)
    private int promotionVersionNumber;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null
                ? LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                : createdAt;
    }

    public enum ReservationStatus { RESERVED, CONFIRMED, RELEASED }
}
