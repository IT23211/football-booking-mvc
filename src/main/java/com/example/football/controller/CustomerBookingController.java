package com.example.football.controller;

import com.example.football.entity.Booking;
import com.example.football.entity.PaymentTransaction;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.CustomerBookingForm;
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
     * Bắt buộc thanh toán 100% qua VNPay/VietQR, giữ chỗ tối đa 5 phút (Anti-Slot Hoarding).
     */
    @PostMapping("/create")
    public String createBooking(@Valid @ModelAttribute("bookingForm") CustomerBookingForm bookingForm,
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
            PaymentTransaction tx = bookingService.createOnlineCustomerBooking(bookingForm, authentication.getName());
            String paymentUrl = vnPayService.createPaymentUrl(tx.getBooking(), tx.getTxnRef(), request);
            return "redirect:" + paymentUrl;
        } catch (SlotAlreadyBookedException | IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/fields/" + bookingForm.getFieldId()
                    + (bookingForm.getBookingDate() != null ? "?date=" + bookingForm.getBookingDate() : "");
        }
    }

    /**
     * Thanh toán tiếp qua VNPay cho đơn đang giữ chỗ chờ thanh toán (PENDING_PAYMENT).
     */
    @GetMapping("/pay/{id}")
    public String retryVnPayPayment(@PathVariable("id") Long bookingId,
                                    Authentication authentication,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        try {
            PaymentTransaction tx = bookingService.createRetryPaymentTransaction(
                    bookingId, authentication.getName(), request.getRemoteAddr());
            String paymentUrl = vnPayService.createPaymentUrl(tx.getBooking(), tx.getTxnRef(), request);
            return "redirect:" + paymentUrl;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/booking/my-bookings";
        }
    }

    /**
     * Hiển thị kết quả đặt sân & mã QR nhận sân (Kiểm tra chính chủ chống IDOR).
     */
    @GetMapping("/result/{id}")
    public String bookingResult(@PathVariable("id") Long bookingId,
                                Authentication authentication,
                                HttpServletRequest request,
                                Model model) {
        Booking booking = bookingService.getBookingForUserCheckingOwnership(
                bookingId, authentication.getName(), request.getRemoteAddr());

        String qrContent = String.format("FOOTBALL_TICKET|CODE=%s|FIELD=%s|DATE=%s|SLOT=%s|TOTAL=%s|STATUS=%s",
                booking.getBookingCode(),
                booking.getField().getFieldCode(),
                booking.getBookingDate(),
                booking.getTimeSlot().getTimeRangeDisplay(),
                booking.getTotalPrice(),
                booking.getStatus().name());
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
     * Khách hàng hủy đơn đặt sân chính chủ (Chống IDOR + Chính sách KHÔNG HOÀN TIỀN).
     */
    @PostMapping("/cancel/{id}")
    public String cancelMyBooking(@PathVariable("id") Long bookingId,
                                  Authentication authentication,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBookingByCustomer(bookingId, authentication.getName(), null, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã hủy đơn đặt sân và mở khóa khung giờ. Lưu ý: Đơn đã thanh toán không được hoàn tiền theo quy định sân bóng.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/booking/my-bookings";
    }
}
