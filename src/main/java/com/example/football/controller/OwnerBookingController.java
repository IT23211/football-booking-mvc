package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.enums.BookingStatus;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.BookingForm;
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

        if (!model.containsAttribute("walkInForm")) {
            model.addAttribute("walkInForm", BookingForm.builder()
                    .bookingDate(LocalDate.now())
                    .paymentMethod("COUNTER_CASH")
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
     * Chủ sân bấm "Xác nhận" đơn đặt sân của khách -> CONFIRMED.
     */
    @PostMapping("/confirm/{id}")
    public String confirmBooking(@PathVariable("id") Long bookingId,
                                 RedirectAttributes redirectAttributes) {
        bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.CONFIRMED);
        redirectAttributes.addFlashAttribute("successMessage", "Đã duyệt xác nhận đơn đặt sân thành công!");
        return "redirect:/owner/bookings";
    }

    /**
     * Chủ sân đánh dấu đơn đã đá xong -> COMPLETED.
     */
    @PostMapping("/complete/{id}")
    public String completeBooking(@PathVariable("id") Long bookingId,
                                  RedirectAttributes redirectAttributes) {
        bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.COMPLETED);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái Hoàn thành cho đơn đặt sân!");
        return "redirect:/owner/bookings";
    }

    /**
     * Chủ sân hủy đơn -> CANCELLED (Mở lại khung giờ cho khách khác đặt).
     */
    @PostMapping("/cancel/{id}")
    public String cancelBooking(@PathVariable("id") Long bookingId,
                                RedirectAttributes redirectAttributes) {
        bookingService.updateBookingStatusByOwner(bookingId, BookingStatus.CANCELLED);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã hủy đơn đặt sân và mở lại khung giờ trống cho khách khác!");
        return "redirect:/owner/bookings";
    }

    /**
     * Chủ sân tạo đơn đặt sân trực tiếp tại quầy cho khách gọi điện / vãng lai.
     */
    @PostMapping("/walk-in")
    public String createWalkInBooking(@Valid @ModelAttribute("walkInForm") BookingForm walkInForm,
                                      BindingResult bindingResult,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/owner/bookings";
        }

        try {
            Booking created = bookingService.createWalkInBookingByOwner(walkInForm, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo đơn đặt sân trực tiếp tại quầy thành công! Mã đơn: " + created.getBookingCode());
        } catch (SlotAlreadyBookedException | IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/bookings";
    }
}

