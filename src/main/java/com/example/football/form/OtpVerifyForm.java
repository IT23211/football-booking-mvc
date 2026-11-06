package com.example.football.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyForm {

    @NotBlank(message = "Địa chỉ Email không được để trống")
    @Email(message = "Địa chỉ Email không hợp lệ")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP 6 chữ số gửi về Email")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải bao gồm đúng 6 chữ số")
    private String otpCode;
}
