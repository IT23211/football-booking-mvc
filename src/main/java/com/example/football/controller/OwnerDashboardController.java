package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.enums.BookingStatus;
import com.example.football.service.AuditLogService;
import com.example.football.service.BookingService;
import com.example.football.service.FootballFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/owner")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerDashboardController {

    private final BookingService bookingService;
    private final FootballFieldService fieldService;
    private final AuditLogService auditLogService;

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        BigDecimal dailyRevenue = bookingService.getDailyRevenue(today);
        BigDecimal monthlyRevenue = bookingService.getMonthlyRevenue(today);
        BigDecimal forfeitedRevenue = bookingService.getTotalForfeitedRevenue();

        long totalBookings = bookingService.countTotalBookings();
        long pendingBookings = bookingService.countBookingsByStatus(BookingStatus.PENDING_PAYMENT);
        long confirmedBookings = bookingService.countBookingsByStatus(BookingStatus.CONFIRMED);
        long completedBookings = bookingService.countBookingsByStatus(BookingStatus.COMPLETED);
        long cancelledOrNoShowBookings = bookingService.countBookingsByStatus(BookingStatus.CANCELLED_NO_REFUND)
                + bookingService.countBookingsByStatus(BookingStatus.NO_SHOW);
        int totalFields = fieldService.getAllFieldsForOwner().size();

        List<Booking> todayBookings = bookingService.getBookingsForOwner(today, null);

        model.addAttribute("today", today);
        model.addAttribute("dailyRevenue", dailyRevenue);
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("forfeitedRevenue", forfeitedRevenue);
        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("pendingBookings", pendingBookings);
        model.addAttribute("confirmedBookings", confirmedBookings);
        model.addAttribute("completedBookings", completedBookings);
        model.addAttribute("cancelledOrNoShowBookings", cancelledOrNoShowBookings);
        model.addAttribute("totalFields", totalFields);
        model.addAttribute("todayBookings", todayBookings);
        model.addAttribute("securityLogs", auditLogService.getRecentSecurityLogs());

        return "owner/dashboard";
    }
}
