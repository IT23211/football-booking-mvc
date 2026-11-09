
package com.example.football.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/account")
@PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
public class AccountController {

    // Trang tài khoản cá nhân
    @GetMapping
    public String profile() {
        return "account/profile";
    }

    // Trang thay đổi Email
    @GetMapping("/change-email")
    public String changeEmail() {
        return "account/change-email";
    }

    // Trang đổi mật khẩu
    @GetMapping("/change-password")
    public String changePassword() {
        return "account/change-password";
    }

    // Trang nhập OTP xác nhận Email mới
   @GetMapping("/change-email/verify-otp")
    public String verifyOtp(Model model) {
        model.addAttribute("otpTitle", "Xác nhận Email mới");
        model.addAttribute("otpDescription",
            "Nhập mã OTP được gửi đến Email mới.");
        model.addAttribute("submitLabel", "Xác nhận Email mới");
        model.addAttribute("otpFieldName", "otp");
        model.addAttribute("verifyAction",
            "/account/change-email/verify-otp");
        model.addAttribute("resendAction",
            "/account/change-email/resend-otp");

        return "account/verify-otp";
    }
}
