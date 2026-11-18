package com.example.football.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO đổi mật khẩu cho người dùng đang đăng nhập (bắt buộc xác thực mã OTP 6 số qua Email).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordForm {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mã OTP 6 chữ số gửi về Email")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP xác thực phải gồm đúng 6 chữ số")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 64, message = "Mật khẩu mới phải từ 6 đến 64 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    private String confirmPassword;
}
