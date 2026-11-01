package com.example.football.integration.payment;

import com.example.football.entity.Booking;
import com.example.football.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final VnPayService vnPayService;
    private final BookingService bookingService;

    /**
     * Màn hình giả lập Cổng thanh toán VNPay Sandbox ngay trên máy Local (khi vnpay.mock-enabled=true).
     * Cho phép kiểm thử trọn vẹn luồng tạo chữ ký số & xác thực chữ ký HMAC-SHA512 mà không cần tài khoản Merchant thật.
     */
    @GetMapping("/mock-vnpay")
    public String showMockVnPayGateway(@RequestParam Map<String, String> allParams, Model model) {
        boolean validRequestSignature = vnPayService.verifySignature(allParams);
        long amountVnd = 0;
        try {
            amountVnd = Long.parseLong(allParams.getOrDefault("vnp_Amount", "0")) / 100;
        } catch (NumberFormatException ignored) {
        }

        String bookingCode = allParams.getOrDefault("vnp_TxnRef", "");
        String qrContent = "VNPAY_SANDBOX|" + bookingCode + "|AMOUNT=" + amountVnd;
        String qrBase64 = vnPayService.generateQrCodeBase64(qrContent, 220, 220);

        model.addAttribute("vnpParams", allParams);
        model.addAttribute("amountVnd", amountVnd);
        model.addAttribute("validRequestSignature", validRequestSignature);
        model.addAttribute("qrBase64", qrBase64);
        return "customer/mock-vnpay";
    }

    /**
     * Xử lý nút bấm "Xác nhận thanh toán thành công (00)" hoặc "Hủy giao dịch (24)" trên cổng VNPay giả lập.
     */
    @PostMapping("/mock-vnpay/submit")
    public String submitMockVnPay(@RequestParam("vnp_TxnRef") String bookingCode,
                                  @RequestParam("vnp_Amount") long amountTimes100,
                                  @RequestParam("responseCode") String responseCode,
                                  @RequestParam(value = "bankCode", defaultValue = "NCB") String bankCode) {
        String signedReturnUrl = vnPayService.buildSignedReturnUrl(bookingCode, amountTimes100, responseCode, bankCode);
        return "redirect:" + signedReturnUrl;
    }

    /**
     * Callback URL khi khách hàng thanh toán xong trên VNPay và được chuyển hướng về (/payment/vnpay-return).
     * Hệ thống bắt buộc kiểm tra chữ ký Checksum HMAC-SHA512 trước khi cập nhật trạng thái đơn.
     */
    @GetMapping("/vnpay-return")
    public String handleVnPayReturn(@RequestParam Map<String, String> requestParams,
                                    RedirectAttributes redirectAttributes) {
        boolean isValidSignature = vnPayService.verifySignature(requestParams);
        if (!isValidSignature) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cảnh báo bảo mật: Chữ ký số VNPay (HMAC-SHA512) không hợp lệ! Giao dịch bị từ chối.");
            return "redirect:/booking/my-bookings";
        }

        try {
            Booking updatedBooking = bookingService.processVnPayCallback(requestParams);
            String responseCode = requestParams.get("vnp_ResponseCode");

            if ("00".equals(responseCode)) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Thanh toán qua VNPay thành công! Đơn đặt sân " + updatedBooking.getBookingCode() + " đã được xác nhận.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Giao dịch thanh toán VNPay không thành công hoặc đã bị hủy (Mã phản hồi: " + responseCode + ").");
            }
            return "redirect:/booking/result/" + updatedBooking.getId();
        } catch (Exception ex) {
            log.error("[VNPAY RETURN ERROR] {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/booking/my-bookings";
        }
    }

    /**
     * Webhook IPN (Instant Payment Notification) nhận thông báo tự động Server-to-Server từ VNPay.
     */
    @RequestMapping(value = "/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleVnPayIpn(@RequestParam Map<String, String> requestParams) {
        if (!vnPayService.verifySignature(requestParams)) {
            return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid Checksum"));
        }

        try {
            bookingService.processVnPayCallback(requestParams);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order Not Found"));
        } catch (Exception ex) {
            log.error("[VNPAY IPN ERROR] {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown Error"));
        }
    }
}

