package com.example.football.integration.sms;

import java.util.regex.Pattern;

public final class VnPhoneUtils {

    /**
     * Regex chuẩn kiểm tra số điện thoại di động Việt Nam 10 chữ số (đầu 03, 05, 07, 08, 09).
     */
    public static final String VN_PHONE_REGEX = "^(0[3|5|7|8|9])+([0-9]{8})$";
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile(VN_PHONE_REGEX);

    private VnPhoneUtils() {
    }

    /**
     * Chuẩn hóa số điện thoại về dạng 10 số bắt đầu bằng 0 (VD: +84912345678 -> 0912345678).
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }
        String cleaned = rawPhone.replaceAll("[\\s.\\-()]", "");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    /**
     * Kiểm tra số điện thoại có hợp lệ theo đầu số nhà mạng Việt Nam hay không.
     */
    public static boolean isValidVnPhone(String phone) {
        String normalized = normalize(phone);
        return VN_PHONE_PATTERN.matcher(normalized).matches();
    }

    /**
     * Chuyển đổi sang định dạng quốc tế 84x dùng khi gọi API SMS Gateway.
     */
    public static String toInternationalFormat(String phone) {
        String normalized = normalize(phone);
        if (normalized.startsWith("0")) {
            return "84" + normalized.substring(1);
        }
        return normalized;
    }
}

