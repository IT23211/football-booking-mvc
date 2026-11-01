package com.example.football.service;

import com.example.football.entity.User;
import com.example.football.enums.Role;
import com.example.football.form.RegisterForm;
import com.example.football.integration.sms.SmsOtpService;
import com.example.football.integration.sms.VnPhoneUtils;
import com.example.football.repository.UserRepository;
import com.example.football.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsOtpService smsOtpService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Đăng ký tài khoản Khách hàng mới.
     * - Chống Mass Assignment: Cưỡng bức gán cứng Role.ROLE_CUSTOMER.
     * - Tài khoản lưu ở trạng thái chưa kích hoạt (enabled = false, phoneVerified = false) chờ xác thực OTP.
     */
    @Transactional
    public User registerCustomer(RegisterForm form) {
        String username = form.getUsername().trim();
        String normalizedPhone = VnPhoneUtils.normalize(form.getPhone());

        if (!VnPhoneUtils.isValidVnPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại không đúng chuẩn nhà mạng Việt Nam!");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp!");
        }

        // Kiểm tra nếu tài khoản chưa kích hoạt đã tồn tại cùng SĐT thì cho phép cập nhật lại và gửi OTP mới
        Optional<User> existingByPhone = userRepository.findByPhone(normalizedPhone);
        if (existingByPhone.isPresent()) {
            User existingUser = existingByPhone.get();
            if (existingUser.isPhoneVerified() && existingUser.isEnabled()) {
                throw new IllegalArgumentException("Số điện thoại này đã được đăng ký và kích hoạt!");
            }
            if (!existingUser.getUsername().equalsIgnoreCase(username) && userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!");
            }
            existingUser.setUsername(username);
            existingUser.setFullName(form.getFullName().trim());
            existingUser.setPassword(passwordEncoder.encode(form.getPassword()));
            existingUser.setRole(Role.ROLE_CUSTOMER); // Cưỡng bức ROLE_CUSTOMER
            existingUser.setEnabled(false);
            existingUser.setPhoneVerified(false);
            User saved = userRepository.save(existingUser);
            smsOtpService.generateAndSendOtp(normalizedPhone);
            return saved;
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!");
        }

        User newUser = User.builder()
                .username(username)
                .fullName(form.getFullName().trim())
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(form.getPassword()))
                .role(Role.ROLE_CUSTOMER) // CƯỠNG BỨC GÁN CỨNG ROLE_CUSTOMER (CHỐNG MASS ASSIGNMENT)
                .phoneVerified(false)
                .enabled(false)
                .build();

        User savedUser = userRepository.save(newUser);
        smsOtpService.generateAndSendOtp(normalizedPhone);

        log.info("[USER REGISTER] Tạo mới tài khoản chờ xác thực OTP: username='{}', phone='{}', role='{}'",
                savedUser.getUsername(), savedUser.getPhone(), savedUser.getRole());

        return savedUser;
    }

    /**
     * Xác thực mã OTP 6 chữ số để kích hoạt tài khoản.
     * Tích hợp chống Brute-Force dò mã OTP (khóa 15 phút nếu nhập sai quá 5 lần).
     */
    @Transactional
    public void verifyOtpAndActivate(String rawPhone, String otpCode) {
        String phone = VnPhoneUtils.normalize(rawPhone);

        if (loginAttemptService.isOtpBlocked(phone)) {
            throw new IllegalStateException("Số điện thoại này đã bị tạm khóa xác thực 15 phút do nhập sai OTP quá 5 lần!");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản gắn với số điện thoại này!"));

        if (user.isEnabled() && user.isPhoneVerified()) {
            return;
        }

        boolean valid = smsOtpService.verifyOtp(phone, otpCode);
        if (!valid) {
            loginAttemptService.otpFailed(phone);
            throw new IllegalArgumentException("Mã OTP không chính xác hoặc đã hết hạn (3 phút)!");
        }

        loginAttemptService.otpSucceeded(phone);
        user.setPhoneVerified(true);
        user.setEnabled(true);
        user.setRole(Role.ROLE_CUSTOMER);
        userRepository.save(user);

        log.info("[OTP VERIFIED] Tài khoản username='{}' (SĐT={}) đã kích hoạt thành công!", user.getUsername(), phone);
    }

    /**
     * Gửi lại mã OTP (chặn spam 60 giây).
     */
    public void resendOtp(String rawPhone) {
        String phone = VnPhoneUtils.normalize(rawPhone);
        if (loginAttemptService.isOtpBlocked(phone)) {
            throw new IllegalStateException("Số điện thoại này đang bị tạm khóa 15 phút do nhập sai OTP quá 5 lần!");
        }
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản cần xác thực với số điện thoại này!"));
        if (user.isEnabled() && user.isPhoneVerified()) {
            throw new IllegalStateException("Tài khoản này đã được kích hoạt trước đó!");
        }
        smsOtpService.generateAndSendOtp(phone);
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng!"));
    }
}

