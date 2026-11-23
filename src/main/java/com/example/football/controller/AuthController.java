package com.example.football.controller;

import com.example.football.entity.User;
import com.example.football.form.OtpVerifyForm;
import com.example.football.form.RegisterForm;
import com.example.football.form.ResetPasswordForm;
import com.example.football.integration.mail.VnPhoneUtils;
import com.example.football.security.LoginAttemptService;
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
    private final LoginAttemptService loginAttemptService;

    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "expired", required = false) String expired,
                                @RequestParam(value = "lockedUser", required = false) String lockedUser,
                                Model model) {
        if (error != null) {
            switch (error) {
                case "locked" -> {
                    if (lockedUser == null || isBruteForceLockActive(lockedUser)) {
                        model.addAttribute("errorMessage",
                                "Tài khoản của bạn đã bị tạm khóa 15 phút do nhập sai mật khẩu quá 5 lần!");
                    }
                }
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
        validateRegisterFormat(registerForm, bindingResult);

        if (isBlank(registerForm.getPassword())
                || isBlank(registerForm.getConfirmPassword())) {
            // @NotBlank on each field is the only warning for an empty value.
        } else if (!registerForm.getPassword().equals(registerForm.getConfirmPassword())) {
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

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage(@RequestParam(value = "email", required = false) String email,
                                         @RequestParam(value = "step", required = false) String step,
                                         Model model) {
        if (!model.containsAttribute("resetPasswordForm")) {
            ResetPasswordForm form = new ResetPasswordForm();
            form.setEmail(email != null ? email : "");
            model.addAttribute("resetPasswordForm", form);
        }
        boolean isResetStep = "reset".equalsIgnoreCase(step) || (email != null && !email.isBlank());
        model.addAttribute("step", isResetStep ? "reset" : "request");
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        String trimmedEmail = email != null ? email.trim() : "";
        try {
            userService.sendForgotPasswordOtp(trimmedEmail);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mã OTP 6 chữ số để đặt lại mật khẩu đã được gửi tới Email " + trimmedEmail + " (Hiệu lực 3 phút)!");
            return "redirect:/auth/forgot-password?step=reset&email=" + trimmedEmail;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ResetPasswordForm form = new ResetPasswordForm();
            form.setEmail(trimmedEmail);
            model.addAttribute("resetPasswordForm", form);
            model.addAttribute("step", "request");
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/forgot-password";
        }
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm resetPasswordForm,
                                       BindingResult bindingResult,
                                       HttpServletRequest request,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        if (resetPasswordForm.getNewPassword() != null
                && !resetPasswordForm.getNewPassword().equals(resetPasswordForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Mật khẩu xác nhận không khớp");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("step", "reset");
            return "auth/forgot-password";
        }

        try {
            userService.resetPasswordWithOtp(resetPasswordForm, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đặt lại mật khẩu mới thành công! Vui lòng đăng nhập bằng mật khẩu mới của bạn.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("step", "reset");
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/forgot-password";
        }
    }

    @RequestMapping("/access-denied")
    public String accessDeniedPage() {
        return "error/403";
    }

    private boolean isBruteForceLockActive(String username) {
        return username != null && !username.isBlank()
                && loginAttemptService.isBlocked(username.trim());
    }

    private void validateRegisterFormat(RegisterForm form, BindingResult bindingResult) {
        if (!isBlank(form.getUsername())) {
            String username = form.getUsername().trim();
            if (username.length() < 4 || username.length() > 30
                    || !username.matches("[a-zA-Z0-9_]+")) {
                bindingResult.rejectValue("username", "error.username",
                        "Tên đăng nhập phải từ 4 đến 30 ký tự và chỉ chứa chữ cái, chữ số và dấu gạch dưới");
            }
        }

        if (!isBlank(form.getFullName())) {
            String fullName = form.getFullName().trim();
            if (fullName.length() < 2 || fullName.length() > 100) {
                bindingResult.rejectValue("fullName", "error.fullName",
                        "Họ và tên phải từ 2 đến 100 ký tự");
            }
        }

        if (!isBlank(form.getEmail())) {
            String email = form.getEmail().trim();
            if (email.length() > 100
                    || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                bindingResult.rejectValue("email", "error.email",
                        "Địa chỉ Email không đúng định dạng");
            }
        }

        if (!isBlank(form.getPhone())) {
            if (!VnPhoneUtils.isValidVnPhone(form.getPhone())) {
                bindingResult.rejectValue("phone", "error.phone",
                        "Số điện thoại Việt Nam không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08, 09)");
            }
        }

        if (!isBlank(form.getPassword())) {
            String password = form.getPassword();
            if (password.length() < 8 || password.length() > 32) {
                bindingResult.rejectValue("password", "error.password",
                        "Mật khẩu phải từ 8 đến 32 ký tự");
            } else {
                boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
                boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
                boolean hasDigit = password.chars().anyMatch(Character::isDigit);
                if (!hasUpper || !hasLower || !hasDigit) {
                    bindingResult.rejectValue("password", "error.password",
                            "Mật khẩu phải bao gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số");
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
