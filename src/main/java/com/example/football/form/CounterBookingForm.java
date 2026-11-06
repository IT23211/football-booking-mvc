package com.example.football.form;

import com.example.football.integration.mail.VnPhoneUtils;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form dành riêng cho Chủ sân (ROLE_OWNER) đặt sân hộ khách trực tiếp tại quầy và thu 100% tiền mặt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterBookingForm {

    @NotNull(message = "Vui lòng chọn sân bóng")
    private Long fieldId;

    @NotNull(message = "Vui lòng chọn ngày đá")
    @FutureOrPresent(message = "Ngày đá không được ở trong quá khứ")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bookingDate;

    @NotNull(message = "Vui lòng chọn khung giờ trống")
    private Long timeSlotId;

    @NotBlank(message = "Vui lòng nhập họ tên khách hàng đặt tại quầy")
    @Size(max = 100, message = "Tên khách hàng tối đa 100 ký tự")
    private String customerName;

    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ của khách")
    @Pattern(regexp = VnPhoneUtils.VN_PHONE_REGEX, message = "Số điện thoại Việt Nam không hợp lệ (10 số)")
    private String customerPhone;

    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String customerEmail;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}

