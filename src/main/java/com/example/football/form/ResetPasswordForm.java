package com.example.football.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO nhận dữ liệu đặt lại mật khẩu qua mã xác thực Email OTP 6 số.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordForm {

    @NotBlank(message = "Địa chỉ Email không được để trống")
    @Email(message = "Địa chỉ Email không hợp lệ")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP 6 chữ số gửi về Email")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải bao gồm đúng 6 chữ số")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 64, message = "Mật khẩu mới phải từ 6 đến 64 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu xác nhận")
    private String confirmPassword;
}
