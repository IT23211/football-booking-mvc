package com.example.football.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlotForm {

    private Long id;

    @NotBlank(message = "Tên ca đá không được để trống")
    @Size(max = 80, message = "Tên ca đá tối đa 80 ký tự")
    private String slotName;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotNull(message = "Phụ phí giờ vàng không được để trống (nhập 0 nếu không có)")
    @DecimalMin(value = "0", message = "Phụ phí giờ vàng không được nhỏ hơn 0")
    @Builder.Default
    private BigDecimal peakHourExtraFee = BigDecimal.ZERO;

    @Builder.Default
    private boolean active = true;
}

