package com.example.football.service;

import com.example.football.entity.User;
import com.example.football.enums.Role;
import com.example.football.form.ChangePasswordForm;
import com.example.football.form.ProfileUpdateForm;
import com.example.football.form.RegisterForm;
import com.example.football.form.ResetPasswordForm;
import com.example.football.integration.mail.EmailOtpService;
import com.example.football.integration.mail.VnPhoneUtils;
import com.example.football.repository.UserRepository;
import com.example.football.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpService emailOtpService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Đăng ký tài khoản Khách hàng mới qua Email OTP.
     * - Chống Mass Assignment: Cưỡng bức gán cứng Role.ROLE_CUSTOMER.
     * - Lưu ở trạng thái chưa kích hoạt (enabled = false, emailVerified = false).
     */
    @Transactional
    public User registerCustomer(RegisterForm form) {
        String username = form.getUsername().trim();
        String email = emailOtpService.normalizeEmail(form.getEmail());
        String normalizedPhone = VnPhoneUtils.normalize(form.getPhone());

        if (!VnPhoneUtils.isValidVnPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại liên hệ không đúng chuẩn nhà mạng Việt Nam!");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp!");
        }

        Optional<User> existingByEmail = userRepository.findByEmail(email);
        if (existingByEmail.isPresent()) {
            User existingUser = existingByEmail.get();
            if (existingUser.isEmailVerified() && existingUser.isEnabled()) {
                throw new IllegalArgumentException("Địa chỉ Email này đã được đăng ký và kích hoạt!");
            }
            if (!existingUser.getUsername().equalsIgnoreCase(username) && userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!");
            }
            if (!existingUser.getPhone().equals(normalizedPhone) && userRepository.existsByPhone(normalizedPhone)) {
                throw new IllegalArgumentException("Số điện thoại này đã được sử dụng bởi tài khoản khác!");
            }
            existingUser.setUsername(username);
            existingUser.setFullName(form.getFullName().trim());
            existingUser.setPhone(normalizedPhone);
            existingUser.setPassword(passwordEncoder.encode(form.getPassword()));
            existingUser.setRole(Role.ROLE_CUSTOMER); // Cưỡng bức ROLE_CUSTOMER
            existingUser.setEnabled(false);
            existingUser.setEmailVerified(false);
            User saved = userRepository.save(existingUser);
            emailOtpService.generateAndSendOtp(email, saved.getFullName());
            return saved;
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!");
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại liên hệ này đã được đăng ký!");
        }

        User newUser = User.builder()
                .username(username)
                .fullName(form.getFullName().trim())
                .email(email)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(form.getPassword()))
                .role(Role.ROLE_CUSTOMER) // CƯỠNG BỨC GÁN CỨNG ROLE_CUSTOMER (CHỐNG MASS ASSIGNMENT)
                .emailVerified(false)
                .strikeCount(0)
                .enabled(false)
                .failedAttempt(0)
                .build();

        User savedUser = userRepository.save(newUser);
        emailOtpService.generateAndSendOtp(email, savedUser.getFullName());

        log.info("[USER REGISTER] Tạo mới tài khoản chờ xác thực Email OTP: username='{}', email='{}', role='{}'",
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());

        return savedUser;
    }

    /**
     * Xác thực mã OTP 6 chữ số gửi về Email để kích hoạt tài khoản.
     */
    @Transactional
    public void verifyOtpAndActivate(String rawEmail, String otpCode, String clientIp) {
        verifyOtpAndActivate(rawEmail, otpCode);
    }

    @Transactional
    public void verifyOtpAndActivate(String rawEmail, String otpCode) {
        String email = emailOtpService.normalizeEmail(rawEmail);

        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email này đã bị tạm khóa xác thực 15 phút do nhập sai OTP quá 5 lần!");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản gắn với địa chỉ Email này!"));

        if (user.isEnabled() && user.isEmailVerified()) {
            return;
        }

        boolean valid = emailOtpService.verifyOtp(email, otpCode);
        if (!valid) {
            loginAttemptService.otpFailed(email);
            throw new IllegalArgumentException("Mã OTP không chính xác hoặc đã hết hạn (3 phút)!");
        }

        loginAttemptService.otpSucceeded(email);
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setRole(Role.ROLE_CUSTOMER);
        userRepository.save(user);

        log.info("[EMAIL OTP VERIFIED] Tài khoản username='{}' (Email={}) đã kích hoạt thành công!", user.getUsername(), email);
    }

    /**
     * Gửi lại mã OTP qua Email (chặn spam 60 giây).
     */
    public void resendOtp(String rawEmail) {
        String email = emailOtpService.normalizeEmail(rawEmail);
        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email này đang bị tạm khóa 15 phút do nhập sai OTP quá 5 lần!");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản cần xác thực với Email này!"));
        if (user.isEnabled() && user.isEmailVerified()) {
            throw new IllegalStateException("Tài khoản này đã được kích hoạt trước đó!");
        }
        emailOtpService.generateAndSendOtp(email, user.getFullName());
    }

    /**
     * Gửi mã OTP 6 chữ số để khôi phục mật khẩu qua Email.
     */
    public void sendForgotPasswordOtp(String rawEmail) {
        String email = emailOtpService.normalizeEmail(rawEmail);
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ Email hợp lệ!");
        }
        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email này đang bị tạm khóa 15 phút do nhập sai OTP quá 5 lần!");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nào được đăng ký với địa chỉ Email này!"));
        emailOtpService.generateAndSendPasswordResetOtp(email, user.getFullName());
    }

    /**
     * Xác thực mã OTP 6 số và đặt lại mật khẩu mới cho tài khoản.
     */
    @Transactional
    public void resetPasswordWithOtp(ResetPasswordForm form, String clientIp) {
        String email = emailOtpService.normalizeEmail(form.getEmail());
        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email này đã bị tạm khóa xác thực 15 phút do nhập sai OTP quá 5 lần!");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp!");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản gắn với địa chỉ Email này!"));

        boolean valid = emailOtpService.verifyOtp(email, form.getOtpCode());
        if (!valid) {
            loginAttemptService.otpFailed(email);
            throw new IllegalArgumentException("Mã OTP không chính xác hoặc đã hết hạn (3 phút)!");
        }

        loginAttemptService.otpSucceeded(email);
        loginAttemptService.loginSucceeded(user.getUsername());
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setFailedAttempt(0);
        user.setLockUntil(null);
        userRepository.save(user);

        log.info("[PASSWORD RESET SUCCESS] Tài khoản username='{}' (Email={}, IP={}) đã đặt lại mật khẩu thành công!",
                user.getUsername(), email, clientIp);
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng!"));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng với ID = " + id));
    }

    @Transactional(readOnly = true)
    public List<User> searchUsersForAdmin(String keyword, Role role) {
        String normalizedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return userRepository.searchUsersForAdmin(normalizedKeyword, role);
    }

    @Transactional(readOnly = true)
    public long countTotalUsers() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public long countUsersByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.countByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public long countDisabledUsers() {
        return userRepository.countByEnabledFalse();
    }

    /**
     * Cập nhật thông tin cá nhân (Họ tên, Số điện thoại) của người dùng đang đăng nhập.
     * Chống Mass Assignment: Chỉ cho phép đổi fullName và phone.
     */
    @Transactional
    public User updateOwnProfile(String username, ProfileUpdateForm form) {
        User user = getByUsername(username);
        String normalizedPhone = VnPhoneUtils.normalize(form.getPhone());

        if (!VnPhoneUtils.isValidVnPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại liên hệ không đúng chuẩn nhà mạng Việt Nam!");
        }

        if (!user.getPhone().equals(normalizedPhone) && userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại này đã được sử dụng bởi một tài khoản khác!");
        }

        user.setFullName(form.getFullName().trim());
        user.setPhone(normalizedPhone);
        User saved = userRepository.save(user);

        log.info("[PROFILE UPDATE] Người dùng '{}' đã cập nhật thông tin cá nhân (Họ tên='{}', SĐT='{}')",
                username, saved.getFullName(), saved.getPhone());
        return saved;
    }

    /**
     * Gửi mã OTP 6 chữ số về Email của người dùng đang đăng nhập để xác nhận đổi mật khẩu.
     */
    public String sendChangePasswordOtp(String username) {
        User user = getByUsername(username);
        String email = emailOtpService.normalizeEmail(user.getEmail());

        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email của bạn đang bị tạm khóa 15 phút do nhập sai OTP quá 5 lần!");
        }

        emailOtpService.generateAndSendPasswordResetOtp(email, user.getFullName());
        return email;
    }

    /**
     * Đổi mật khẩu cho người dùng đang đăng nhập (yêu cầu xác nhận mật khẩu hiện tại VÀ mã OTP 6 chữ số qua Email).
     */
    @Transactional
    public void changeOwnPassword(String username, ChangePasswordForm form) {
        User user = getByUsername(username);
        String email = emailOtpService.normalizeEmail(user.getEmail());

        if (loginAttemptService.isOtpBlocked(email)) {
            throw new IllegalStateException("Địa chỉ Email của bạn đã bị tạm khóa xác thực 15 phút do nhập sai OTP quá 5 lần!");
        }

        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp với mật khẩu mới!");
        }

        if (passwordEncoder.matches(form.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu hiện tại!");
        }

        boolean validOtp = emailOtpService.verifyOtp(email, form.getOtpCode());
        if (!validOtp) {
            loginAttemptService.otpFailed(email);
            throw new IllegalArgumentException("Mã OTP xác thực không chính xác hoặc đã hết hạn (3 phút)! Vui lòng bấm 'Nhận mã OTP qua Email' để lấy mã mới.");
        }

        loginAttemptService.otpSucceeded(email);
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);

        log.info("[PASSWORD CHANGED WITH OTP] Người dùng '{}' (Email={}) đã xác thực OTP và đổi mật khẩu thành công!",
                username, email);
    }

    /**
     * Chủ sân khóa hoặc mở khóa tài khoản người dùng.
     */
    @Transactional
    public User toggleUserStatusByOwner(Long userId, String ownerUsername) {
        User target = getById(userId);
        if (target.getUsername().equalsIgnoreCase(ownerUsername)) {
            throw new IllegalStateException("Bạn không thể tự khóa tài khoản Chủ sân của chính mình!");
        }

        boolean currentlyBlocked = !target.isEnabled() || target.isCurrentlyLocked();
        if (currentlyBlocked) {
            target.setEnabled(true);
            target.setEmailVerified(true);
            target.setFailedAttempt(0);
            target.setLockUntil(null);
            target.setStrikeCount(0);
            loginAttemptService.loginSucceeded(target.getUsername());
            log.info("[ADMIN USER UNLOCK] Chủ sân '{}' đã mở khóa tài khoản '{}'", ownerUsername, target.getUsername());
        } else {
            target.setEnabled(false);
            log.info("[ADMIN USER LOCK] Chủ sân '{}' đã khóa tài khoản '{}'", ownerUsername, target.getUsername());
        }

        return userRepository.save(target);
    }

    /**
     * Chủ sân xóa điểm phạt giữ chỗ quá hạn (strikeCount) và mở khóa tạm thời cho khách hàng.
     */
    @Transactional
    public User resetUserStrikesByOwner(Long userId, String ownerUsername) {
        User target = getById(userId);
        target.setStrikeCount(0);
        target.setFailedAttempt(0);
        target.setLockUntil(null);
        target.setEnabled(true);
        loginAttemptService.loginSucceeded(target.getUsername());
        User saved = userRepository.save(target);

        log.info("[ADMIN RESET STRIKES] Chủ sân '{}' đã xóa gậy phạt và mở khóa cho tài khoản '{}'",
                ownerUsername, saved.getUsername());
        return saved;
    }
}
