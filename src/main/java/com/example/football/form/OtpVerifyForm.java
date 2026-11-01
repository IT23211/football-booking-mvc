package com.example.football.form;

import com.example.football.integration.sms.VnPhoneUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyForm {

    @NotBlank(message = "Số điện thoại không hợp lệ")
    @Pattern(regexp = VnPhoneUtils.VN_PHONE_REGEX, message = "Số điện thoại Việt Nam không hợp lệ")
    private String phone;

    @NotBlank(message = "Vui lòng nhập mã OTP 6 chữ số")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải bao gồm đúng 6 chữ số")
    private String otpCode;
}

