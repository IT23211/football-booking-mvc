package com.example.football.repository;

import com.example.football.entity.Booking;
import com.example.football.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Lấy danh sách ID các khung giờ đang bị khóa (PENDING_PAYMENT, CONFIRMED, COMPLETED)
     * của một sân bóng trong ngày được chọn.
     * Khi đơn chuyển sang EXPIRED_UNPAID, CANCELLED_NO_REFUND hoặc NO_SHOW, khung giờ tự động mở khóa!
     */
    @Query("""
            SELECT b.timeSlot.id FROM Booking b
            WHERE b.field.id = :fieldId
              AND b.bookingDate = :bookingDate
              AND b.status IN (
                  com.example.football.enums.BookingStatus.PENDING_PAYMENT,
                  com.example.football.enums.BookingStatus.CONFIRMED,
                  com.example.football.enums.BookingStatus.COMPLETED
              )
            """)
    List<Long> findLockedTimeSlotIds(@Param("fieldId") Long fieldId,
                                     @Param("bookingDate") LocalDate bookingDate);

    /**
     * Khóa bi quan (PESSIMISTIC_WRITE) kiểm tra xem khung giờ đã có người giữ chỗ hoặc chốt đơn chưa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b
            WHERE b.field.id = :fieldId
              AND b.timeSlot.id = :timeSlotId
              AND b.bookingDate = :bookingDate
              AND b.status IN (
                  com.example.football.enums.BookingStatus.PENDING_PAYMENT,
                  com.example.football.enums.BookingStatus.CONFIRMED,
                  com.example.football.enums.BookingStatus.COMPLETED
              )
            """)
    List<Booking> findActiveBookingsWithLock(@Param("fieldId") Long fieldId,
                                             @Param("timeSlotId") Long timeSlotId,
                                             @Param("bookingDate") LocalDate bookingDate);

    /**
     * Kiểm tra xem khách hàng hiện tại có đang giữ chỗ đơn nào ở trạng thái PENDING_PAYMENT chưa hết hạn không.
     * (Quy tắc: Mỗi tài khoản chỉ được giữ tối đa 1 đơn PENDING_PAYMENT tại một thời điểm).
     */
    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.user.id = :userId
              AND b.status = com.example.football.enums.BookingStatus.PENDING_PAYMENT
              AND b.holdExpiresAt > :now
            """)
    long countActivePendingBookingsByUser(@Param("userId") Long userId,
                                          @Param("now") LocalDateTime now);

    /**
     * Quét toàn bộ các đơn PENDING_PAYMENT đã quá hạn 5 phút để BookingCleanupScheduler
     * tự động chuyển sang EXPIRED_UNPAID, nhả khóa sân và cộng +1 Strike cho User.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE b.status = com.example.football.enums.BookingStatus.PENDING_PAYMENT
              AND b.holdExpiresAt <= :now
            """)
    List<Booking> findExpiredPendingBookingsWithLock(@Param("now") LocalDateTime now);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE b.bookingCode = :bookingCode
            """)
    Optional<Booking> findByBookingCode(@Param("bookingCode") String bookingCode);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE u.username = :username
            ORDER BY b.bookingDate DESC, ts.startTime DESC, b.createdAt DESC
            """)
    List<Booking> findByUsernameWithDetails(@Param("username") String username);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE (:bookingDate IS NULL OR b.bookingDate = :bookingDate)
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.bookingDate DESC, ts.startTime ASC, b.createdAt DESC
            """)
    List<Booking> findAllForOwner(@Param("bookingDate") LocalDate bookingDate,
                                  @Param("status") BookingStatus status);

    /**
     * Tính tổng doanh thu theo ngày: Bao gồm cả các đơn đã thanh toán 100% (PAID)
     * VÀ các đơn khách hủy/bỏ sân không hoàn tiền (FORFEITED).
     */
    @Query("""
            SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b
            WHERE b.bookingDate = :date
              AND b.paymentStatus IN (
                  com.example.football.enums.PaymentStatus.PAID,
                  com.example.football.enums.PaymentStatus.FORFEITED
              )
            """)
    BigDecimal calculateDailyRevenue(@Param("date") LocalDate date);

    /**
     * Tính tổng doanh thu trong khoảng ngày (bao gồm PAID + FORFEITED).
     */
    @Query("""
            SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b
            WHERE b.bookingDate BETWEEN :startDate AND :endDate
              AND b.paymentStatus IN (
                  com.example.football.enums.PaymentStatus.PAID,
                  com.example.football.enums.PaymentStatus.FORFEITED
              )
            """)
    BigDecimal calculateRevenueBetween(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /**
     * Tính tổng số tiền bồi thường từ các đơn khách hủy/bỏ sân không hoàn tiền (FORFEITED).
     */
    @Query("""
            SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b
            WHERE b.paymentStatus = com.example.football.enums.PaymentStatus.FORFEITED
            """)
    BigDecimal calculateTotalForfeitedRevenue();

    long countByStatus(BookingStatus status);
}
