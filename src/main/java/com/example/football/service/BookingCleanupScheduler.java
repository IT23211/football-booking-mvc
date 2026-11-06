package com.example.football.service;

import com.example.football.entity.Booking;
import com.example.football.entity.User;
import com.example.football.enums.BookingStatus;
import com.example.football.enums.PaymentStatus;
import com.example.football.enums.SecurityEventType;
import com.example.football.repository.BookingRepository;
import com.example.football.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tiến trình chạy ngầm mỗi 60 giây (@Scheduled):
 * - Tự động quét các đơn PENDING_PAYMENT đã quá hạn giữ chỗ 5 phút.
 * - Đổi trạng thái đơn sang EXPIRED_UNPAID -> Cột ảo active_slot_lock về NULL -> Nhả khóa sân ngay lập tức.
 * - Cộng +1 vào strike_count của User đó.
 * - Nếu strike_count >= 3 (tạo đơn giữ chỗ rồi bỏ ngang không thanh toán 3 lần),
 *   tự động khóa tài khoản (enabled = false, lock_until = +24 giờ) và ghi log ACCOUNT_LOCKED_STRIKES.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupScheduler {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Value("${booking.max-strikes:3}")
    private int maxStrikes;

    @Value("${booking.strike-lock-hours:24}")
    private int strikeLockHours;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanupExpiredPendingBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookingsWithLock(now);

        if (expiredBookings.isEmpty()) {
            return;
        }

        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.EXPIRED_UNPAID);
            booking.setPaymentStatus(PaymentStatus.UNPAID);
            booking.setCancelReason("Hệ thống tự động hủy do quá thời hạn giữ chỗ 5 phút không thanh toán 100%.");
            bookingRepository.save(booking);

            User user = booking.getUser();
            if (user != null) {
                int newStrikes = user.getStrikeCount() + 1;
                user.setStrikeCount(newStrikes);

                auditLogService.logSecurityEvent(
                        user.getId(),
                        user.getUsername(),
                        SecurityEventType.SLOT_HOARDING_STRIKE,
                        "/booking/hold-expired/" + booking.getBookingCode(),
                        "SYSTEM_SCHEDULER",
                        "Đơn " + booking.getBookingCode() + " quá hạn 5 phút không thanh toán. Strike hiện tại: " + newStrikes + "/" + maxStrikes
                );

                if (newStrikes >= maxStrikes) {
                    user.setEnabled(false);
                    user.setLockUntil(now.plusHours(strikeLockHours));
                    auditLogService.logSecurityEvent(
                            user.getId(),
                            user.getUsername(),
                            SecurityEventType.ACCOUNT_LOCKED_STRIKES,
                            "/auth/login",
                            "SYSTEM_SCHEDULER",
                            "Khóa tài khoản '" + user.getUsername() + "' do bỏ cọc giữ chỗ ảo " + newStrikes + " lần liên tiếp!"
                    );
                    log.warn("[ANTI-HOARDING LOCK] Tài khoản '{}' đã bị khóa do vi phạm giữ chỗ ảo {} lần!",
                            user.getUsername(), newStrikes);
                }

                userRepository.save(user);
            }

            log.info("[AUTO EXPIRED 5M] Đã thu hồi khung giờ của đơn quá hạn {} (Sân: {}, Ngày: {}, Ca: {})",
                    booking.getBookingCode(),
                    booking.getField().getName(),
                    booking.getBookingDate(),
                    booking.getTimeSlot().getSlotName());
        }
    }
}

