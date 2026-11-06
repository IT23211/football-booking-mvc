package com.example.football.entity;

import com.example.football.enums.PaymentChannel;
import com.example.football.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_booking", columnList = "booking_id"),
        @Index(name = "idx_payment_txn_ref", columnList = "txn_ref", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_booking"))
    private Booking booking;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 50)
    private String txnRef;

    @Column(name = "provider_txn_no", length = 100)
    private String providerTxnNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_channel", nullable = false, length = 25)
    @Builder.Default
    private PaymentChannel paymentChannel = PaymentChannel.VNPAY;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "secure_hash", length = 255)
    private String secureHash;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
