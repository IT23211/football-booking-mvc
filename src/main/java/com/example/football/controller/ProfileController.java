package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.entity.User;
import com.example.football.enums.BookingStatus;
import com.example.football.enums.PaymentStatus;
import com.example.football.form.ChangePasswordForm;
import com.example.football.form.ProfileUpdateForm;
import com.example.football.service.BookingService;
import com.example.football.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/profile")
@PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final BookingService bookingService;

    @GetMapping
    public String viewProfile(Authentication authentication, Model model) {
        populateProfileData(authentication.getName(), model);

        if (!model.containsAttribute("profileUpdateForm")) {
            User user = (User) model.getAttribute("userProfile");
            ProfileUpdateForm updateForm = new ProfileUpdateForm();
            if (user != null) {
                updateForm.setFullName(user.getFullName());
                updateForm.setPhone(user.getPhone());
            }
            model.addAttribute("profileUpdateForm", updateForm);
        }

        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute("changePasswordForm", new ChangePasswordForm());
        }

        return "customer/profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @Valid @ModelAttribute("profileUpdateForm") ProfileUpdateForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateProfileData(authentication.getName(), model);
            model.addAttribute("changePasswordForm", new ChangePasswordForm());
            model.addAttribute("errorMessage", "Vui lòng kiểm tra lại thông tin họ tên hoặc số điện thoại!");
            return "customer/profile";
        }

        try {
            userService.updateOwnProfile(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật thông tin cá nhân thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/send-change-password-otp")
    public String sendChangePasswordOtp(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            String email = userService.sendChangePasswordOtp(authentication.getName());
            redirectAttributes.addFlashAttribute("otpSentMessage",
                    "Đã gửi mã xác thực OTP 6 chữ số tới Email " + email + " (hiệu lực 3 phút). Vui lòng nhập mã OTP bên dưới để đổi mật khẩu!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile#change-password-card";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateProfileData(authentication.getName(), model);
            User user = (User) model.getAttribute("userProfile");
            ProfileUpdateForm updateForm = new ProfileUpdateForm();
            if (user != null) {
                updateForm.setFullName(user.getFullName());
                updateForm.setPhone(user.getPhone());
            }
            model.addAttribute("profileUpdateForm", updateForm);
            model.addAttribute("errorMessage",
                    "Vui lòng nhập đầy đủ mật khẩu hiện tại, mã OTP 6 chữ số đã gửi về Email và mật khẩu mới (tối thiểu 6 ký tự)!");
            return "customer/profile";
        }

        try {
            userService.changeOwnPassword(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác thực OTP thành công và cập nhật mật khẩu mới cho tài khoản!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/profile";
    }

    private void populateProfileData(String username, Model model) {
        User user = userService.getByUsername(username);
        List<Booking> bookings = bookingService.getBookingsByUsername(username);

        long totalBookings = bookings.size();
        long confirmedOrCompletedCount = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
                .count();
        BigDecimal totalPaidAmount = bookings.stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.PAID || b.getPaymentStatus() == PaymentStatus.FORFEITED)
                .map(Booking::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Booking> recentBookings = bookings.size() > 5 ? bookings.subList(0, 5) : bookings;

        model.addAttribute("userProfile", user);
        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("confirmedOrCompletedCount", confirmedOrCompletedCount);
        model.addAttribute("totalPaidAmount", totalPaidAmount);
        model.addAttribute("recentBookings", recentBookings);
    }
}
