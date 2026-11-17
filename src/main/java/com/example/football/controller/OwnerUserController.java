package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.entity.User;
import com.example.football.enums.BookingStatus;
import com.example.football.enums.PaymentStatus;
import com.example.football.enums.Role;
import com.example.football.service.BookingService;
import com.example.football.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/owner/users")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerUserController {

    private final UserService userService;
    private final BookingService bookingService;

    @GetMapping
    public String listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            Model model) {

        List<User> users = userService.searchUsersForAdmin(keyword, role);

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("roles", Role.values());
        model.addAttribute("totalUsers", userService.countTotalUsers());
        model.addAttribute("totalCustomers", userService.countUsersByRole(Role.ROLE_CUSTOMER));
        model.addAttribute("totalOwners", userService.countUsersByRole(Role.ROLE_OWNER));
        model.addAttribute("disabledUsers", userService.countDisabledUsers());

        return "owner/user-list";
    }

    @GetMapping("/{id}")
    public String viewUserDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            User targetUser = userService.getById(id);
            List<Booking> userBookings = bookingService.getBookingsByUsername(targetUser.getUsername());

            long totalBookings = userBookings.size();
            long confirmedOrCompletedCount = userBookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
                    .count();
            long cancelledOrExpiredCount = userBookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CANCELLED_NO_REFUND
                            || b.getStatus() == BookingStatus.EXPIRED_UNPAID
                            || b.getStatus() == BookingStatus.NO_SHOW)
                    .count();
            BigDecimal totalSpent = userBookings.stream()
                    .filter(b -> b.getPaymentStatus() == PaymentStatus.PAID || b.getPaymentStatus() == PaymentStatus.FORFEITED)
                    .map(Booking::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("targetUser", targetUser);
            model.addAttribute("userBookings", userBookings);
            model.addAttribute("totalBookings", totalBookings);
            model.addAttribute("confirmedOrCompletedCount", confirmedOrCompletedCount);
            model.addAttribute("cancelledOrExpiredCount", cancelledOrExpiredCount);
            model.addAttribute("totalSpent", totalSpent);

            return "owner/user-detail";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/users";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "list") String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User updated = userService.toggleUserStatusByOwner(id, authentication.getName());
            String statusText = updated.isEnabled() ? "Đã mở khóa & kích hoạt" : "Đã khóa";
            redirectAttributes.addFlashAttribute("successMessage",
                    statusText + " tài khoản @" + updated.getUsername() + " (" + updated.getFullName() + ") thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "detail".equalsIgnoreCase(returnTo) ? "redirect:/owner/users/" + id : "redirect:/owner/users";
    }

    @PostMapping("/{id}/reset-strikes")
    public String resetUserStrikes(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "detail") String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User updated = userService.resetUserStrikesByOwner(id, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xóa gậy phạt giữ chỗ quá hạn và mở khóa tài khoản @" + updated.getUsername() + "!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "detail".equalsIgnoreCase(returnTo) ? "redirect:/owner/users/" + id : "redirect:/owner/users";
    }
}
