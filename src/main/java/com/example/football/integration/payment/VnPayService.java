package com.example.football.integration.payment;

import com.example.football.entity.Booking;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@Getter
public class VnPayService {

    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${vnpay.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${vnpay.tmn-code:DEMO_TMN_CODE}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:DEMO_VNPAY_HASH_SECRET_KEY_2026_FOOTBALL_BOOKING}")
    private String hashSecret;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${vnpay.return-url:http://localhost:8080/payment/vnpay-return}")
    private String returnUrl;

    @Value("${vnpay.version:2.1.0}")
    private String version;

    @Value("${vnpay.command:pay}")
    private String command;

    @Value("${vnpay.order-type:billpayment}")
    private String orderType;

    /**
     * Tạo URL thanh toán VNPay kèm chữ ký điện tử HMAC-SHA512.
     * Nếu vnpay.mock-enabled=true thì chuyển tới màn hình cổng VNPay giả lập nội bộ để test ngay không cần tài khoản thật.
     */
    public String createPaymentUrl(Booking booking, HttpServletRequest request) {
        long amountInVndTimes100 = booking.getTotalPrice()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String createDate = now.format(VNP_DATE_FORMAT);
        String expireDate = now.plusMinutes(15).format(VNP_DATE_FORMAT);

        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", version);
        vnpParams.put("vnp_Command", command);
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amountInVndTimes100));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", booking.getBookingCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan dat san bong ma don " + booking.getBookingCode());
        vnpParams.put("vnp_OrderType", orderType);
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", getClientIp(request));
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        String queryUrl = buildQueryString(vnpParams);
        String vnpSecureHash = hmacSha512(hashSecret, queryUrl);
        String fullQuery = queryUrl + "&vnp_SecureHash=" + vnpSecureHash;

        if (mockEnabled) {
            return request.getContextPath() + "/payment/mock-vnpay?" + fullQuery;
        }
        return payUrl + "?" + fullQuery;
    }

    /**
     * Tạo URL callback trả về từ trang giả lập VNPay Sandbox với chữ ký số HMAC-SHA512 chuẩn xác.
     */
    public String buildSignedReturnUrl(String bookingCode, long amountTimes100, String responseCode, String bankCode) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", String.valueOf(amountTimes100));
        params.put("vnp_BankCode", bankCode != null ? bankCode : "NCB");
        params.put("vnp_BankTranNo", "VNP" + System.currentTimeMillis());
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan dat san bong ma don " + bookingCode);
        params.put("vnp_PayDate", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(VNP_DATE_FORMAT));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_TransactionNo", String.valueOf(System.currentTimeMillis()).substring(3));
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TxnRef", bookingCode);

        String hashData = buildQueryString(params);
        String secureHash = hmacSha512(hashSecret, hashData);
        return "/payment/vnpay-return?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    /**
     * Xác thực chữ ký Checksum HMAC-SHA512 từ VNPay trả về để chống chỉnh sửa tham số trên URL.
     */
    public boolean verifySignature(Map<String, String> requestParams) {
        if (requestParams == null || !requestParams.containsKey("vnp_SecureHash")) {
            return false;
        }

        String receivedHash = requestParams.get("vnp_SecureHash");
        Map<String, String> sortedParams = new TreeMap<>();

        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("vnp_")
                    && !"vnp_SecureHash".equalsIgnoreCase(key)
                    && !"vnp_SecureHashType".equalsIgnoreCase(key)
                    && value != null && !value.isBlank()) {
                sortedParams.put(key, value);
            }
        }

        String hashData = buildQueryString(sortedParams);
        String calculatedHash = hmacSha512(hashSecret, hashData);

        boolean valid = MessageDigest.isEqual(
                calculatedHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
                receivedHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            log.warn("[SECURITY VNPAY CHECKSUM ALERT] Chữ ký HMAC-SHA512 không hợp lệ! Received={}, Calculated={}",
                    receivedHash, calculatedHash);
        }
        return valid;
    }

    /**
     * Băm dữ liệu bằng thuật toán HMAC-SHA512 thông qua thư viện Apache Commons Codec.
     */
    public String hmacSha512(String key, String data) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_512, key).hmacHex(data);
    }

    /**
     * Sinh ảnh mã QR dạng Base64 PNG bằng thư viện Google ZXing (hiển thị vé điện tử / mã thanh toán).
     */
    public String generateQrCodeBase64(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.warn("Không thể tạo mã QR: {}", e.getMessage());
            return null;
        }
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, String>> itr = params.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            if (fieldValue != null && !fieldValue.isEmpty()) {
                sb.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    sb.append('&');
                }
            }
        }
        return sb.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

