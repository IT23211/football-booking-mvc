package com.example.football;

import com.example.football.config.CacheConfig;
import com.example.football.entity.Booking;
import com.example.football.entity.FootballField;
import com.example.football.entity.TimeSlot;
import com.example.football.form.RegisterForm;
import com.example.football.integration.mail.EmailOtpService;
import com.example.football.integration.mail.VnPhoneUtils;
import com.example.football.integration.payment.VnPayService;
import com.example.football.security.LoginAttemptService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FootballBookingApplicationTests {

    @Test
    @DisplayName("1. Kiểm tra chuẩn hóa & Regex số điện thoại Việt Nam (VnPhoneUtils)")
    void testVnPhoneUtils() {
        assertTrue(VnPhoneUtils.isValidVnPhone("0912345678"));
        assertTrue(VnPhoneUtils.isValidVnPhone("0387654321"));
        assertTrue(VnPhoneUtils.isValidVnPhone("0567891234"));
        assertTrue(VnPhoneUtils.isValidVnPhone("0791234567"));
        assertTrue(VnPhoneUtils.isValidVnPhone("0834567890"));
        assertTrue(VnPhoneUtils.isValidVnPhone("+84912345678"));

        assertFalse(VnPhoneUtils.isValidVnPhone("0123456789")); // Đầu 01 cũ không hợp lệ
        assertFalse(VnPhoneUtils.isValidVnPhone("091234567"));  // Thiếu số (9 số)
        assertFalse(VnPhoneUtils.isValidVnPhone("09123456789")); // Thừa số (11 số)
        assertFalse(VnPhoneUtils.isValidVnPhone("abc1234567"));
    }

    @Test
    @DisplayName("2. Kiểm tra sinh Email OTP 6 số, xác thực 1 lần và chặn spam 60 giây (EmailOtpService)")
    void testEmailOtpService() {
        CacheConfig cacheConfig = new CacheConfig();
        ReflectionTestUtils.setField(cacheConfig, "otpExpirationMinutes", 3L);
        ReflectionTestUtils.setField(cacheConfig, "otpCooldownSeconds", 60L);

        Cache<String, String> otpCache = cacheConfig.otpCache();
        Cache<String, Long> cooldownCache = cacheConfig.otpCooldownCache();

        EmailOtpService emailOtpService = new EmailOtpService(otpCache, cooldownCache, null);
        ReflectionTestUtils.setField(emailOtpService, "mockEnabled", true);
        ReflectionTestUtils.setField(emailOtpService, "cooldownSeconds", 60L);

        String email = "khachhang1@gmail.com";
        String otp = emailOtpService.generateAndSendOtp(email);
        assertNotNull(otp);
        assertEquals(6, otp.length());

        // Chặn gửi lại ngay lập tức trong 60s
        assertThrows(IllegalStateException.class, () -> emailOtpService.generateAndSendOtp(email));

        // Nhập sai mã OTP
        assertFalse(emailOtpService.verifyOtp(email, "000000"));

        // Nhập đúng mã OTP -> Thành công và tự hủy ngay sau khi dùng
        assertTrue(emailOtpService.verifyOtp(email, otp));
        assertFalse(emailOtpService.verifyOtp(email, otp));
    }

    @Test
    @DisplayName("3. Kiểm tra chống Brute-Force khóa tài khoản sau 5 lần sai (LoginAttemptService)")
    void testBruteForceProtection() {
        LoginAttemptService attemptService = new LoginAttemptService();
        String username = "attacker_user";

        for (int i = 0; i < 4; i++) {
            attemptService.loginFailed(username);
            assertFalse(attemptService.isBlocked(username));
        }

        // Lần thứ 5 -> Bị khóa
        attemptService.loginFailed(username);
        assertTrue(attemptService.isBlocked(username));

        // Sau khi đăng nhập thành công -> Mở khóa
        attemptService.loginSucceeded(username);
        assertFalse(attemptService.isBlocked(username));
    }

    @Test
    @DisplayName("4. Kiểm tra chữ ký số HMAC-SHA512 VNPay (100% giá trị đơn), chống giả mạo URL & tạo QR Base64")
    void testVnPayHmacSha512AndQrCode() {
        VnPayService vnPayService = new VnPayService();
        ReflectionTestUtils.setField(vnPayService, "mockEnabled", true);
        ReflectionTestUtils.setField(vnPayService, "tmnCode", "DEMO_TMN");
        ReflectionTestUtils.setField(vnPayService, "hashSecret", "SECRET_KEY_2026_FOOTBALL_BOOKING");
        ReflectionTestUtils.setField(vnPayService, "returnUrl", "http://localhost:8080/payment/vnpay-return");
        ReflectionTestUtils.setField(vnPayService, "version", "2.1.0");
        ReflectionTestUtils.setField(vnPayService, "command", "pay");
        ReflectionTestUtils.setField(vnPayService, "orderType", "billpayment");

        FootballField field = FootballField.builder()
                .id(1L)
                .fieldCode("SAN-5-A1")
                .name("Sân A1")
                .basePricePerSlot(new BigDecimal("300000"))
                .build();
        TimeSlot slot = TimeSlot.builder()
                .id(1L)
                .slotName("Ca 1")
                .startTime(LocalTime.of(17, 30))
                .endTime(LocalTime.of(19, 0))
                .peakHourExtraFee(new BigDecimal("100000"))
                .build();
        Booking booking = Booking.builder()
                .id(10L)
                .bookingCode("FB2610319999")
                .field(field)
                .timeSlot(slot)
                .bookingDate(LocalDate.now())
                .fieldPrice(new BigDecimal("300000"))
                .extraFee(new BigDecimal("100000"))
                .totalPrice(new BigDecimal("400000"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        String txnRef = "TXN2610319999";
        String paymentUrl = vnPayService.createPaymentUrl(booking, txnRef, new MockHttpServletRequest());
        assertTrue(paymentUrl.contains("vnp_SecureHash="));

        // Kiểm tra hàm tạo chữ ký trả về và xác thực chữ ký
        String returnUrl = vnPayService.buildSignedReturnUrl(txnRef, 40000000L, "00", "NCB");
        String queryPart = returnUrl.substring(returnUrl.indexOf('?') + 1);
        Map<String, String> params = new HashMap<>();
        for (String pair : queryPart.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.US_ASCII));
        }

        assertTrue(vnPayService.verifySignature(params));

        // Giả mạo số tiền trên URL -> Chữ ký HMAC-SHA512 phải báo không hợp lệ!
        params.put("vnp_Amount", "1000");
        assertFalse(vnPayService.verifySignature(params));

        // Kiểm tra tạo mã QR ZXing
        String qrBase64 = vnPayService.generateQrCodeBase64("FOOTBALL_BOOKING_TEST", 200, 200);
        assertNotNull(qrBase64);
        assertTrue(qrBase64.startsWith("data:image/png;base64,"));
    }

    @Test
    @DisplayName("5. Kiểm tra chống Mass Assignment: RegisterForm tuyệt đối không chứa trường role hay enabled")
    void testMassAssignmentProtection() {
        Field[] fields = RegisterForm.class.getDeclaredFields();
        boolean hasRoleOrEnabled = Arrays.stream(fields)
                .anyMatch(f -> f.getName().equalsIgnoreCase("role") || f.getName().equalsIgnoreCase("enabled"));
        assertFalse(hasRoleOrEnabled, "RegisterForm không được chứa trường role hoặc enabled!");
    }
}
