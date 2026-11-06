package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.enums.BookingStatus;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.CounterBookingForm;
import com.example.football.service.BookingService;
import com.example.football.service.FootballFieldService;
import com.example.football.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final BookingService bookingService;
    private final FootballFieldService fieldService;
    private final TimeSlotService timeSlotService;

    @GetMapping
    public String listBookings(@RequestParam(value = "date", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(value = "status", required = false) BookingStatus status,
                               Model model) {
        List<Booking> bookings = bookingService.getBookingsForOwner(date, status);

        if (!model.containsAttribute("counterForm")) {
            model.addAttribute("counterForm", CounterBookingForm.builder()
                    .bookingDate(LocalDate.now())
                    .build());
        }

        model.addAttribute("bookings", bookings);
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("bookingStatuses", BookingStatus.values());
        model.addAttribute("activeFields", fieldService.getAllActiveFields());
        model.addAttribute("activeTimeSlots", timeSlotService.getActiveTimeSlots());
        model.addAttribute("today", LocalDate.now());

        return "owner/booking-manage";
    }

    /**
     * Màn hình riêng cho Chủ sân tạo đơn đặt sân trực tiếp tại quầy (COUNTER_OWNER - Thu 100% tiền mặt).
     */
    @GetMapping("/counter-new")
    public String showCounterBookingForm(Model model) {
        if (!model.containsAttribute("counterForm")) {
            model.addAttribute("counterForm", CounterBookingForm.builder()
                    .bookingDate(LocalDate.now())
                    .build());
        }
        model.addAttribute("activeFields", fieldService.getAllActiveFields());
        model.addAttribute("activeTimeSlots", timeSlotService.getActiveTimeSlots());
        model.addAttribute("today", LocalDate.now());
        return "owner/counter-booking-form";
    }

    /**
     * Chủ sân lưu đơn đặt sân trực tiếp tại quầy (COUNTER_OWNER - Thu 100% tiền mặt tại quầy).
     */
    @PostMapping({"/counter-save", "/walk-in"})
    public String createCounterBooking(@Valid @ModelAttribute("counterForm") CounterBookingForm counterForm,
                                       BindingResult bindingResult,
                                       Authentication authentication,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeFields", fieldService.getAllActiveFields());
            model.addAttribute("activeTimeSlots", timeSlotService.getActiveTimeSlots());
            model.addAttribute("today", LocalDate.now());
            return "owner/counter-booking-form";
        }

        try {
            Booking created = bookingService.createCounterBookingByOwner(counterForm, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo đơn tại quầy và thu 100% tiền mặt thành công! Mã đơn: " + created.getBookingCode());
            return "redirect:/owner/bookings";
        } catch (SlotAlreadyBookedException | IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activeFields", fieldService.getAllActiveFields());
            model.addAttribute("activeTimeSlots", timeSlotService.getActiveTimeSlots());
            model.addAttribute("today", LocalDate.now());
            return "owner/counter-booking-form";
        }
    }

    /**
     * Chủ sân đánh dấu đơn đã đá xong -> COMPLETED.
     */
    @PostMapping("/complete/{id}")
    public String completeBooking(@PathVariable("id") Long bookingId,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.COMPLETED, authentication.getName(), null);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái Hoàn thành (COMPLETED) cho đơn đặt sân!");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/bookings";
    }

    /**
     * Chủ sân đánh dấu khách bỏ sân không đến -> NO_SHOW (Mất 100% tiền FORFEITED, mở khóa sân bán lại).
     */
    @PostMapping("/no-show/{id}")
    public String markNoShowBooking(@PathVariable("id") Long bookingId,
                                    @RequestParam(value = "reason", required = false) String reason,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.NO_SHOW, authentication.getName(), reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã đánh dấu khách Vắng mặt (NO_SHOW), giữ 100% doanh thu (FORFEITED) và mở khóa lại sân!");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/bookings";
    }

    /**
     * Chủ sân hủy đơn không hoàn tiền -> CANCELLED_NO_REFUND (Mở lại khung giờ cho khách khác đặt).
     */
    @PostMapping("/cancel/{id}")
    public String cancelBooking(@PathVariable("id") Long bookingId,
                                @RequestParam(value = "reason", required = false) String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.CANCELLED_NO_REFUND, authentication.getName(), reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã hủy đơn đặt sân (Không hoàn tiền - FORFEITED) và mở lại khung giờ trống cho khách khác!");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/bookings";
    }
}
