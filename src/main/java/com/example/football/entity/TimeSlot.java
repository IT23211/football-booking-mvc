package com.example.football.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots", indexes = {
        @Index(name = "idx_timeslot_start", columnList = "start_time")
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

    @Column(name = "slot_name", nullable = false, length = 80)
    private String slotName;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "peak_hour_extra_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal peakHourExtraFee = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    protected void onCreate() {
        if (this.peakHourExtraFee == null) {
            this.peakHourExtraFee = BigDecimal.ZERO;
        }
    }

    public String getTimeRangeDisplay() {
        return String.format("%s - %s", startTime, endTime);
    }

    /**
     * Kiểm tra an toàn xem ca đá có phụ phí giờ cao điểm (> 0) hay không,
     * tránh lỗi so sánh scale của BigDecimal (0.00 vs 0) trên Thymeleaf.
     */
    public boolean hasExtraFee() {
        return peakHourExtraFee != null && peakHourExtraFee.compareTo(BigDecimal.ZERO) > 0;
    }
}
