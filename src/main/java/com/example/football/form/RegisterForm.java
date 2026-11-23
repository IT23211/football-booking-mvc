package com.example.football.form;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO nhận dữ liệu đăng ký tài khoản khách hàng.
 * TUYỆT ĐỐI KHÔNG CHỨA THUỘC TÍNH 'role' hay 'enabled' để chặn đứng Mass Assignment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Địa chỉ Email không được để trống")
    private String email;

    @NotBlank(message = "Số điện thoại liên hệ không được để trống")
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu xác nhận")
    private String confirmPassword;
}
