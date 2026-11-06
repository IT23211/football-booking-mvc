package com.example.football.controller;

import com.example.football.entity.User;
import com.example.football.form.OtpVerifyForm;
import com.example.football.form.RegisterForm;
import com.example.football.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "expired", required = false) String expired,
                                Model model) {
        if (error != null) {
            switch (error) {
                case "locked" -> model.addAttribute("errorMessage",
                        "Tài khoản hoặc IP của bạn đã bị tạm khóa (do nhập sai quá 5 lần hoặc giữ chỗ sân không thanh toán quá 3 lần)!");
                case "disabled" -> model.addAttribute("errorMessage",
                        "Tài khoản của bạn chưa được xác thực mã OTP qua Email!");
                default -> model.addAttribute("errorMessage",
                        "Tên đăng nhập hoặc mật khẩu không chính xác!");
            }
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất khỏi hệ thống an toàn.");
        }
        if (expired != null) {
            model.addAttribute("errorMessage", "Phiên đăng nhập của bạn đã hết hạn hoặc được đăng nhập từ thiết bị khác.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "auth/register";
    }

    /**
     * Xử lý đăng ký tài khoản khách hàng & gửi mã OTP 6 số về Email.
     * Chỉ nhận dữ liệu qua RegisterForm (không có trường role) -> Chống Mass Assignment.
     */
    @PostMapping("/register")
    public String processRegister(@Valid @ModelAttribute("registerForm") RegisterForm registerForm,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (!registerForm.getPassword().equals(registerForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Mật khẩu xác nhận không khớp");
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            User savedUser = userService.registerCustomer(registerForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mã xác thực OTP 6 chữ số đã được gửi tới Email " + savedUser.getEmail()
                            + " (Kiểm tra cửa sổ Console của IDE nếu đang chạy chế độ Local Mock).");
            return "redirect:/auth/verify-otp?email=" + savedUser.getEmail();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/verify-otp")
    public String showVerifyOtpPage(@RequestParam(value = "email", required = false) String email,
                                    Model model) {
        if (!model.containsAttribute("otpVerifyForm")) {
            OtpVerifyForm form = new OtpVerifyForm();
            form.setEmail(email != null ? email : "");
            model.addAttribute("otpVerifyForm", form);
        }
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String processVerifyOtp(@Valid @ModelAttribute("otpVerifyForm") OtpVerifyForm otpVerifyForm,
                                   BindingResult bindingResult,
                                   HttpServletRequest request,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/verify-otp";
        }

        try {
            userService.verifyOtpAndActivate(
                    otpVerifyForm.getEmail(),
                    otpVerifyForm.getOtpCode(),
                    request.getRemoteAddr()
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Xác thực Email thành công! Tài khoản của bạn đã được kích hoạt, vui lòng đăng nhập.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/verify-otp";
        }
    }

    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam("email") String email,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.resendOtp(email);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã gửi lại mã OTP 6 chữ số mới tới hộp thư " + email + "!");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/auth/verify-otp?email=" + email;
    }

    @RequestMapping("/access-denied")
    public String accessDeniedPage() {
        return "error/403";
    }
}
