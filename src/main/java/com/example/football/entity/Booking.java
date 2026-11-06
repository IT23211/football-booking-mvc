package com.example.football.entity;

import com.example.football.enums.BookingSource;
import com.example.football.enums.BookingStatus;
import com.example.football.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_status_hold", columnList = "status, hold_expires_at"),
        @Index(name = "idx_booking_date_status", columnList = "booking_date, status"),
        @Index(name = "idx_booking_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    /**
     * Có thể NULL nếu khách vãng lai đặt trực tiếp tại quầy qua Chủ sân.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_booking_user"))
    private User user;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 15)
    private String customerPhone;

    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", nullable = false, length = 25)
    @Builder.Default
    private BookingSource bookingSource = BookingSource.ONLINE_CUSTOMER;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false, foreignKey = @ForeignKey(name = "fk_booking_field"))
    private FootballField field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_booking_timeslot"))
    private TimeSlot timeSlot;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "field_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal fieldPrice;

    @Column(name = "extra_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal extraFee = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "hold_expires_at", nullable = false)
    private LocalDateTime holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_owner", foreignKey = @ForeignKey(name = "fk_booking_owner"))
    private User createdByOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", foreignKey = @ForeignKey(name = "fk_booking_cancelled_by"))
    private User cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /**
     * Cột ảo (Generated Column) do MySQL 8 tự động tính toán:
     * = 1 khi status IN ('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED')
     * = NULL khi đơn hết hạn 5p (EXPIRED_UNPAID), hủy (CANCELLED_NO_REFUND) hoặc bỏ sân (NO_SHOW).
     */
    @Column(name = "active_slot_lock", insertable = false, updatable = false)
    private Byte activeSlotLock;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.holdExpiresAt == null) {
            this.holdExpiresAt = now.plusMinutes(5);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
