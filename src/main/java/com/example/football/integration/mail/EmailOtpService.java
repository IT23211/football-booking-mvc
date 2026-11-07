package com.example.football.integration.mail;

import com.example.football.entity.Booking;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class EmailOtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, String> otpCache;
    private final Cache<String, Long> otpCooldownCache;
    private final JavaMailSender mailSender;

    @Value("${mail.otp.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${mail.otp.expiration-minutes:3}")
    private long expirationMinutes;

    @Value("${mail.otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${spring.mail.username:noreply@footballvn.com}")
    private String fromEmail;

    public EmailOtpService(@Qualifier("otpCache") Cache<String, String> otpCache,
                           @Qualifier("otpCooldownCache") Cache<String, Long> otpCooldownCache,
                           JavaMailSender mailSender) {
        this.otpCache = otpCache;
        this.otpCooldownCache = otpCooldownCache;
        this.mailSender = mailSender;
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public boolean isInCooldown(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        return otpCooldownCache.getIfPresent(email) != null;
    }

    public long getRemainingCooldownSeconds(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Long sentAt = otpCooldownCache.getIfPresent(email);
        if (sentAt == null) {
            return 0;
        }
        long elapsedSeconds = (System.currentTimeMillis() - sentAt) / 1000;
        return Math.max(0, cooldownSeconds - elapsedSeconds);
    }

    /**
     * Sinh mã OTP 6 chữ số (overload mặc định khi không truyền fullName).
     */
    public String generateAndSendOtp(String rawEmail) {
        return generateAndSendOtp(rawEmail, "Khách hàng");
    }

    /**
     * Sinh mã OTP 6 chữ số, lưu vào Caffeine Cache 3 phút (180 giây), chặn spam 60 giây
     * và gửi Email HTML (hoặc in ra Console nếu mail.otp.mock-enabled=true).
     */
    public String generateAndSendOtp(String rawEmail, String fullName) {
        String email = normalizeEmail(rawEmail);
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Địa chỉ Email nhận mã OTP không hợp lệ!");
        }

        if (isInCooldown(email)) {
            long remaining = getRemainingCooldownSeconds(email);
            throw new IllegalStateException("Vui lòng đợi " + remaining + " giây trước khi yêu cầu gửi lại mã OTP qua Email!");
        }

        int number = 100000 + SECURE_RANDOM.nextInt(900000);
        String otpCode = String.valueOf(number);

        otpCache.put(email, otpCode);
        otpCooldownCache.put(email, System.currentTimeMillis());

        log.info("\n" +
                "====================================================================\n" +
                " [EMAIL OTP LOG - KIỂM TRA TRÊN TERMINAL]\n" +
                " -> Họ tên khách hàng  : {}\n" +
                " -> Email nhận mã OTP  : {}\n" +
                " -> MÃ XÁC THỰC OTP    : {}\n" +
                " -> Thời hạn hiệu lực  : {} phút ({} giây)\n" +
                " -> Chế độ gửi Gmail   : {}\n" +
                "====================================================================",
                fullName, email, otpCode, expirationMinutes, expirationMinutes * 60,
                mockEnabled ? "MOCK LOCAL (Chỉ in Terminal)" : "GỬI EMAIL THẬT QUA GMAIL SMTP (" + fromEmail + ")");

        if (!mockEnabled && mailSender != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> sendHtmlOtpEmailAsync(email, fullName, otpCode));
        }

        return otpCode;
    }

    /**
     * Sinh mã OTP 6 chữ số để đặt lại mật khẩu khi người dùng quên mật khẩu.
     */
    public String generateAndSendPasswordResetOtp(String rawEmail, String fullName) {
        String email = normalizeEmail(rawEmail);
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Địa chỉ Email nhận mã OTP không hợp lệ!");
        }

        if (isInCooldown(email)) {
            long remaining = getRemainingCooldownSeconds(email);
            throw new IllegalStateException("Vui lòng đợi " + remaining + " giây trước khi yêu cầu gửi lại mã OTP qua Email!");
        }

        int number = 100000 + SECURE_RANDOM.nextInt(900000);
        String otpCode = String.valueOf(number);

        otpCache.put(email, otpCode);
        otpCooldownCache.put(email, System.currentTimeMillis());

        log.info("\n" +
                "====================================================================\n" +
                " [EMAIL OTP ĐẶT LẠI MẬT KHẨU - KIỂM TRA TRÊN TERMINAL]\n" +
                " -> Họ tên người dùng  : {}\n" +
                " -> Email nhận mã OTP  : {}\n" +
                " -> MÃ XÁC THỰC OTP    : {}\n" +
                " -> Thời hạn hiệu lực  : {} phút ({} giây)\n" +
                " -> Chế độ gửi Gmail   : {}\n" +
                "====================================================================",
                fullName, email, otpCode, expirationMinutes, expirationMinutes * 60,
                mockEnabled ? "MOCK LOCAL (Chỉ in Terminal)" : "GỬI EMAIL THẬT QUA GMAIL SMTP (" + fromEmail + ")");

        if (!mockEnabled && mailSender != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> sendHtmlPasswordResetOtpEmailAsync(email, fullName, otpCode));
        }

        return otpCode;
    }

    /**
     * Kiểm tra mã OTP 6 chữ số từ Email (Chống Timing Attack & xóa ngay sau khi khớp 1 lần).
     */
    public boolean verifyOtp(String rawEmail, String inputOtp) {
        if (rawEmail == null || inputOtp == null || inputOtp.isBlank()) {
            return false;
        }
        String email = normalizeEmail(rawEmail);
        String cachedOtp = otpCache.getIfPresent(email);
        if (cachedOtp == null) {
            return false;
        }

        boolean matched = MessageDigest.isEqual(
                cachedOtp.getBytes(StandardCharsets.UTF_8),
                inputOtp.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (matched) {
            otpCache.invalidate(email);
            otpCooldownCache.invalidate(email);
        }
        return matched;
    }

    @Async
    public void sendHtmlOtpEmailAsync(String toEmail, String fullName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[FOOTBALL BOOKING VN] Mã xác thực OTP kích hoạt tài khoản của bạn");

            String htmlContent = """
                    <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden;">
                        <div style="background: #146c43; color: #ffffff; padding: 24px; text-align: center;">
                            <h2 style="margin: 0;">⚽ FOOTBALL BOOKING VN</h2>
                            <p style="margin: 6px 0 0; opacity: 0.9;">Xác thực tài khoản Đặt sân bóng đá trực tuyến</p>
                        </div>
                        <div style="padding: 28px; background: #ffffff; color: #333333;">
                            <p>Xin chào <strong>%s</strong>,</p>
                            <p>Cảm ơn bạn đã đăng ký tài khoản tại hệ thống Đặt sân bóng đá trực tuyến. Mã xác thực OTP (6 chữ số) của bạn là:</p>
                            <div style="text-align: center; margin: 24px 0;">
                                <span style="display: inline-block; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #146c43; background: #e8f5e9; padding: 14px 28px; border-radius: 8px; border: 2px dashed #198754;">%s</span>
                            </div>
                            <p style="color: #dc3545; font-weight: 600;">⚠️ Lưu ý: Mã OTP chỉ có hiệu lực trong vòng %d phút. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>
                        </div>
                    </div>
                    """.formatted(fullName, otpCode, expirationMinutes);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL OTP SENT] Đã gửi mã OTP tới địa chỉ Email: {}", toEmail);
        } catch (Exception ex) {
            log.error("[EMAIL OTP ERROR] Không thể gửi Email OTP tới {}: {}", toEmail, ex.getMessage());
        }
    }

    @Async
    public void sendHtmlPasswordResetOtpEmailAsync(String toEmail, String fullName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[FOOTBALL BOOKING VN] Mã OTP đặt lại mật khẩu tài khoản của bạn");

            String htmlContent = """
                    <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden;">
                        <div style="background: #146c43; color: #ffffff; padding: 24px; text-align: center;">
                            <h2 style="margin: 0;">⚽ FOOTBALL BOOKING VN</h2>
                            <p style="margin: 6px 0 0; opacity: 0.9;">Khôi phục mật khẩu tài khoản Đặt sân bóng đá trực tuyến</p>
                        </div>
                        <div style="padding: 28px; background: #ffffff; color: #333333;">
                            <p>Xin chào <strong>%s</strong>,</p>
                            <p>Hệ thống vừa nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Mã xác thực OTP (6 chữ số) để khôi phục mật khẩu là:</p>
                            <div style="text-align: center; margin: 24px 0;">
                                <span style="display: inline-block; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #146c43; background: #e8f5e9; padding: 14px 28px; border-radius: 8px; border: 2px dashed #198754;">%s</span>
                            </div>
                            <p style="color: #dc3545; font-weight: 600;">⚠️ Lưu ý: Mã OTP chỉ có hiệu lực trong vòng %d phút. Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                        </div>
                    </div>
                    """.formatted(fullName, otpCode, expirationMinutes);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[PASSWORD RESET OTP SENT] Đã gửi mã OTP đặt lại mật khẩu tới Email: {}", toEmail);
        } catch (Exception ex) {
            log.error("[PASSWORD RESET OTP ERROR] Không thể gửi Email OTP tới {}: {}", toEmail, ex.getMessage());
        }
    }

    /**
     * Gửi Email biên lai xác nhận đặt sân thành công sau khi khách hàng thanh toán 100% qua VNPay.
     */
    @Async
    public void sendBookingConfirmationEmailAsync(Booking booking) {
        String toEmail = booking.getCustomerEmail();
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        if (mockEnabled) {
            log.info("\n" +
                    "====================================================================\n" +
                    " [EMAIL BIÊN LAI ĐẶT SÂN 100% - MOCK CONSOLE]\n" +
                    " -> Gửi tới Email : {}\n" +
                    " -> Mã đặt sân    : {}\n" +
                    " -> Sân bóng      : {} ({})\n" +
                    " -> Ngày & Ca đá  : {} | {}\n" +
                    " -> Đã thanh toán : {} VNĐ (100% - Không hoàn tiền)\n" +
                    "====================================================================",
                    toEmail, booking.getBookingCode(), booking.getField().getName(),
                    booking.getField().getFieldCode(),
                    booking.getBookingDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    booking.getTimeSlot().getSlotName(), booking.getPaidAmount());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[FOOTBALL BOOKING VN] Biên lai xác nhận đặt sân #" + booking.getBookingCode());

            String html = """
                    <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 620px; margin: 0 auto; border: 1px solid #dee2e6; border-radius: 12px; overflow: hidden;">
                        <div style="background: #146c43; color: #ffffff; padding: 20px; text-align: center;">
                            <h2 style="margin: 0;">XÁC NHẬN ĐẶT SÂN THÀNH CÔNG (ĐÃ THANH TOÁN 100%%)</h2>
                            <p style="margin: 5px 0 0;">Mã đặt sân: <strong>%s</strong></p>
                        </div>
                        <div style="padding: 24px;">
                            <p>Xin chào <strong>%s</strong>,</p>
                            <p>Đơn đặt sân của bạn đã được thanh toán 100%% và khóa lịch thành công:</p>
                            <ul style="line-height: 1.8;">
                                <li><strong>Sân bóng:</strong> %s (%s)</li>
                                <li><strong>Ngày đá:</strong> %s</li>
                                <li><strong>Khung giờ:</strong> %s (%s)</li>
                                <li><strong>Tổng tiền đã thanh toán (100%%):</strong> %s VNĐ</li>
                            </ul>
                            <p style="background: #fff3cd; padding: 12px; border-radius: 6px; color: #664d03; font-size: 13px;">
                                <strong>Quy định:</strong> Đơn đã thanh toán 100%% không áp dụng hoàn tiền dưới mọi hình thức khi hủy hoặc vắng mặt.
                            </p>
                        </div>
                    </div>
                    """.formatted(
                    booking.getBookingCode(),
                    booking.getCustomerName(),
                    booking.getField().getName(),
                    booking.getField().getFieldCode(),
                    booking.getBookingDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    booking.getTimeSlot().getSlotName(),
                    booking.getTimeSlot().getTimeRangeDisplay(),
                    booking.getPaidAmount().toPlainString()
            );

            helper.setText(html, true);
            mailSender.send(message);
            log.info("[BOOKING RECEIPT EMAIL] Đã gửi biên lai đặt sân {} tới {}", booking.getBookingCode(), toEmail);
        } catch (Exception ex) {
            log.error("[BOOKING RECEIPT EMAIL ERROR] Lỗi gửi biên lai cho đơn {}: {}", booking.getBookingCode(), ex.getMessage());
        }
    }
}

