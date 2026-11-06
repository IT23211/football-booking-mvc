package com.example.football.form;

import com.example.football.enums.PaymentChannel;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form đặt sân trực tuyến cho Khách hàng (Bắt buộc thanh toán 100% qua VNPay/VietQR - Không đặt cọc).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerBookingForm {

    @NotNull(message = "Vui lòng chọn sân bóng")
    private Long fieldId;

    @NotNull(message = "Vui lòng chọn ngày đặt sân")
    @FutureOrPresent(message = "Ngày đặt sân không được ở trong quá khứ")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bookingDate;

    @NotNull(message = "Vui lòng chọn khung giờ đá còn trống")
    private Long timeSlotId;

    @Builder.Default
    private PaymentChannel paymentChannel = PaymentChannel.VNPAY;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}

