package com.example.football.form;

import com.example.football.integration.mail.VnPhoneUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO cập nhật thông tin cá nhân (Chống Mass Assignment: Chỉ cho phép sửa fullName và phone).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateForm {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @NotBlank(message = "Số điện thoại liên hệ không được để trống")
    @Pattern(regexp = VnPhoneUtils.VN_PHONE_REGEX, message = "Số điện thoại Việt Nam không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08, 09)")
    private String phone;
}
