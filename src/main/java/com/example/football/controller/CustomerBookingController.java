package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.BookingForm;
import com.example.football.integration.payment.VnPayService;
import com.example.football.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/booking")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final BookingService bookingService;
    private final VnPayService vnPayService;

    /**
     * Xử lý đặt sân trực tuyến từ trang chi tiết sân bóng.
     */
    @PostMapping("/create")
    public String createBooking(@Valid @ModelAttribute("bookingForm") BookingForm bookingForm,
                                BindingResult bindingResult,
                                Authentication authentication,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", firstError);
            return "redirect:/fields/" + bookingForm.getFieldId()
                    + (bookingForm.getBookingDate() != null ? "?date=" + bookingForm.getBookingDate() : "");
        }

        try {
            Booking booking = bookingService.createCustomerBooking(bookingForm, authentication.getName());

            // Nếu khách chọn thanh toán qua VNPay -> Tạo URL kèm chữ ký HMAC-SHA512 và chuyển hướng
            if ("VNPAY".equalsIgnoreCase(booking.getPaymentMethod())) {
                String paymentUrl = vnPayService.createPaymentUrl(booking, request);
                return "redirect:" + paymentUrl;
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Đặt sân thành công! Đơn của bạn đang chờ Chủ sân duyệt xác nhận.");
            return "redirect:/booking/result/" + booking.getId();
        } catch (SlotAlreadyBookedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/fields/" + bookingForm.getFieldId() + "?date=" + bookingForm.getBookingDate();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/fields/" + bookingForm.getFieldId() + "?date=" + bookingForm.getBookingDate();
        }
    }

    /**
     * Hiển thị kết quả đặt sân & mã QR nhận sân (Kiểm tra chính chủ chống IDOR).
     */
    @GetMapping("/result/{id}")
    public String bookingResult(@PathVariable("id") Long bookingId,
                                Authentication authentication,
                                Model model) {
        Booking booking = bookingService.getBookingForUserCheckingOwnership(bookingId, authentication.getName());
        String qrContent = String.format("FOOTBALL_TICKET|CODE=%s|FIELD=%s|DATE=%s|SLOT=%s|AMOUNT=%s",
                booking.getBookingCode(),
                booking.getField().getName(),
                booking.getBookingDate(),
                booking.getTimeSlot().getTimeRangeDisplay(),
                booking.getTotalPrice());
        String qrBase64 = vnPayService.generateQrCodeBase64(qrContent, 220, 220);

        model.addAttribute("booking", booking);
        model.addAttribute("qrBase64", qrBase64);
        return "customer/booking-result";
    }

    /**
     * Lịch sử đặt sân của khách hàng đang đăng nhập.
     */
    @GetMapping("/my-bookings")
    public String myBookings(Authentication authentication, Model model) {
        List<Booking> bookings = bookingService.getBookingsByUsername(authentication.getName());
        model.addAttribute("bookings", bookings);
        return "customer/my-bookings";
    }

    /**
     * Khách hàng hủy đơn đặt sân chính chủ (Chống IDOR).
     */
    @PostMapping("/cancel/{id}")
    public String cancelMyBooking(@PathVariable("id") Long bookingId,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBookingByCustomer(bookingId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn đặt sân thành công!");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/booking/my-bookings";
    }
}

