package com.example.football.form;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingForm {

    @NotNull(message = "Vui lòng chọn sân bóng")
    private Long fieldId;

    @NotNull(message = "Vui lòng chọn ngày đặt sân")
    @FutureOrPresent(message = "Ngày đặt sân không được ở trong quá khứ")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bookingDate;

    @NotNull(message = "Vui lòng chọn khung giờ đá")
    private Long timeSlotId;

    @NotBlank(message = "Vui lòng chọn phương thức thanh toán")
    @Builder.Default
    private String paymentMethod = "VNPAY";

    @Size(max = 100, message = "Tên khách hàng tối đa 100 ký tự")
    private String customerName;

    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    private String customerPhone;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}

