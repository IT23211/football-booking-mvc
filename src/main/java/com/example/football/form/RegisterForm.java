package com.example.football.form;

import com.example.football.integration.sms.VnPhoneUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO nhận dữ liệu đăng ký tài khoản khách hàng.
 * TUYỆT ĐỐI KHÔNG CHỨA THUỘC TÍNH 'role' hay 'enabled' để ngăn chặn triệt để lỗ hổng Mass Assignment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 4, max = 30, message = "Tên đăng nhập phải từ 4 đến 30 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Tên đăng nhập chỉ được chứa chữ cái, chữ số và dấu gạch dưới")
    private String username;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = VnPhoneUtils.VN_PHONE_REGEX, message = "Số điện thoại Việt Nam không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08, 09)")
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 64, message = "Mật khẩu phải từ 6 đến 64 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu xác nhận")
    private String confirmPassword;
}

