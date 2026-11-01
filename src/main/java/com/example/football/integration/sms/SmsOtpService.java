package com.example.football.integration.sms;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SmsOtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, String> otpCache;
    private final Cache<String, Long> otpCooldownCache;
    private final RestClient restClient;

    @Value("${sms.otp.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${sms.otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${sms.otp.api-url:https://api.speedsms.vn/index.php/sms/send}")
    private String smsApiUrl;

    @Value("${sms.otp.access-token:DEMO_TOKEN}")
    private String smsAccessToken;

    @Value("${sms.otp.sender:FOOTBALL_VN}")
    private String smsSender;

    public SmsOtpService(@Qualifier("otpCache") Cache<String, String> otpCache,
                         @Qualifier("otpCooldownCache") Cache<String, Long> otpCooldownCache) {
        this.otpCache = otpCache;
        this.otpCooldownCache = otpCooldownCache;
        this.restClient = RestClient.create();
    }

    /**
     * Kiểm tra xem số điện thoại có đang trong thời gian chờ 60 giây chống spam SMS hay không.
     */
    public boolean isInCooldown(String rawPhone) {
        String phone = VnPhoneUtils.normalize(rawPhone);
        return otpCooldownCache.getIfPresent(phone) != null;
    }

    /**
     * Tính số giây còn lại trước khi được phép gửi lại mã OTP.
     */
    public long getRemainingCooldownSeconds(String rawPhone) {
        String phone = VnPhoneUtils.normalize(rawPhone);
        Long sentAt = otpCooldownCache.getIfPresent(phone);
        if (sentAt == null) {
            return 0;
        }
        long elapsedSeconds = (System.currentTimeMillis() - sentAt) / 1000;
        return Math.max(0, cooldownSeconds - elapsedSeconds);
    }

    /**
     * Sinh mã OTP 6 chữ số ngẫu nhiên, lưu Caffeine Cache (3 phút) và gửi SMS.
     */
    public String generateAndSendOtp(String rawPhone) {
        String phone = VnPhoneUtils.normalize(rawPhone);
        if (!VnPhoneUtils.isValidVnPhone(phone)) {
            throw new IllegalArgumentException("Số điện thoại không đúng định dạng nhà mạng Việt Nam!");
        }

        if (isInCooldown(phone)) {
            long remaining = getRemainingCooldownSeconds(phone);
            throw new IllegalStateException("Vui lòng đợi " + remaining + " giây trước khi yêu cầu gửi lại mã OTP!");
        }

        int number = 100000 + SECURE_RANDOM.nextInt(900000);
        String otpCode = String.valueOf(number);

        otpCache.put(phone, otpCode);
        otpCooldownCache.put(phone, System.currentTimeMillis());

        String message = String.format("[FOOTBALL BOOKING] Ma xac thuc OTP cua ban la: %s. Ma co hieu luc trong 3 phut. Tuyet doi khong chia se ma nay.", otpCode);

        if (mockEnabled) {
            log.info("\n" +
                    "====================================================================\n" +
                    " [SMS OTP MOCK CONSOLE - CHẾ ĐỘ TEST LOCAL]\n" +
                    " -> Số điện thoại nhận : {} ({})\n" +
                    " -> MÃ XÁC THỰC OTP    : {}\n" +
                    " -> Thời hạn hiệu lực  : 3 phút (180 giây)\n" +
                    "====================================================================",
                    phone, VnPhoneUtils.toInternationalFormat(phone), otpCode);
        } else {
            sendRealSmsViaGateway(phone, message);
        }

        return otpCode;
    }

    /**
     * Kiểm tra mã OTP người dùng nhập vào (So sánh an toàn chống Timing Attack & xóa ngay sau khi khớp).
     */
    public boolean verifyOtp(String rawPhone, String inputOtp) {
        if (rawPhone == null || inputOtp == null || inputOtp.isBlank()) {
            return false;
        }
        String phone = VnPhoneUtils.normalize(rawPhone);
        String cachedOtp = otpCache.getIfPresent(phone);
        if (cachedOtp == null) {
            return false;
        }

        boolean matched = MessageDigest.isEqual(
                cachedOtp.getBytes(StandardCharsets.UTF_8),
                inputOtp.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (matched) {
            otpCache.invalidate(phone);
            otpCooldownCache.invalidate(phone);
        }
        return matched;
    }

    private void sendRealSmsViaGateway(String phone, String content) {
        try {
            Map<String, Object> payload = Map.of(
                    "to", List.of(VnPhoneUtils.toInternationalFormat(phone)),
                    "content", content,
                    "sms_type", 2,
                    "sender", smsSender
            );

            restClient.post()
                    .uri(smsApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(smsAccessToken, "x"))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[SMS GATEWAY] Đã gửi OTP thành công tới SĐT {}", phone);
        } catch (Exception ex) {
            log.error("[SMS GATEWAY ERROR] Không thể kết nối SMS Gateway cho SĐT {}: {}", phone, ex.getMessage());
            throw new IllegalStateException("Không thể gửi tin nhắn SMS lúc này. Vui lòng thử lại sau!");
        }
    }
}

