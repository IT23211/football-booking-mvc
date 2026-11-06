package com.example.football.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_maintenance_logs", indexes = {
        @Index(name = "idx_maint_field_date", columnList = "field_id, maintenance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldMaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maint_field"))
    private FootballField field;

    @Column(name = "maintenance_date", nullable = false)
    private LocalDate maintenanceDate;

    /**
     * Nếu timeSlot = null: Khóa bảo trì toàn bộ các ca trong ngày đó.
     * Nếu timeSlot != null: Chỉ khóa bảo trì riêng ca đá được chọn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", foreignKey = @ForeignKey(name = "fk_maint_slot"))
    private TimeSlot timeSlot;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_maint_creator"))
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

