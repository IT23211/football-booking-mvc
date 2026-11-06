package com.example.football.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_timeslot_range", columnNames = {"start_time", "end_time"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_name", nullable = false, length = 50)
    private String slotName;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_peak_hour", nullable = false)
    @Builder.Default
    private boolean peakHour = false;

    @Column(name = "peak_hour_extra_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal peakHourExtraFee = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (this.peakHourExtraFee == null) {
            this.peakHourExtraFee = BigDecimal.ZERO;
        }
        this.peakHour = this.peakHourExtraFee.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getTimeRangeDisplay() {
        return String.format("%s - %s", startTime, endTime);
    }

    public boolean hasExtraFee() {
        return peakHourExtraFee != null && peakHourExtraFee.compareTo(BigDecimal.ZERO) > 0;
    }
}
