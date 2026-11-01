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
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Lấy danh sách ID các khung giờ đã được đặt (trạng thái khác CANCELLED)
     * của một sân cụ thể trong ngày được chọn để hiển thị Trống / Đã kín trên giao diện.
     */
    @Query("""
            SELECT b.timeSlot.id FROM Booking b
            WHERE b.field.id = :fieldId
              AND b.bookingDate = :bookingDate
              AND b.status <> com.example.football.enums.BookingStatus.CANCELLED
            """)
    List<Long> findBookedTimeSlotIds(@Param("fieldId") Long fieldId,
                                     @Param("bookingDate") LocalDate bookingDate);

    /**
     * Khóa bi quan (PESSIMISTIC_WRITE - SELECT ... FOR UPDATE) chống Race Condition
     * khi 2 khách hàng cùng bấm đặt 1 sân, cùng 1 ca, cùng 1 ngày tại cùng 1 thời điểm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b
            WHERE b.field.id = :fieldId
              AND b.timeSlot.id = :timeSlotId
              AND b.bookingDate = :bookingDate
              AND b.status <> com.example.football.enums.BookingStatus.CANCELLED
            """)
    List<Booking> findActiveBookingsWithLock(@Param("fieldId") Long fieldId,
                                             @Param("timeSlotId") Long timeSlotId,
                                             @Param("bookingDate") LocalDate bookingDate);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.field
            JOIN FETCH b.timeSlot
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.field
            JOIN FETCH b.timeSlot
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
            JOIN FETCH b.user u
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            WHERE (:bookingDate IS NULL OR b.bookingDate = :bookingDate)
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.bookingDate DESC, ts.startTime ASC, b.createdAt DESC
            """)
    List<Booking> findAllForOwner(@Param("bookingDate") LocalDate bookingDate,
                                  @Param("status") BookingStatus status);

    @Query("""
            SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b
            WHERE b.bookingDate = :date
              AND b.status IN (com.example.football.enums.BookingStatus.CONFIRMED,
                               com.example.football.enums.BookingStatus.COMPLETED)
            """)
    BigDecimal calculateDailyRevenue(@Param("date") LocalDate date);

    @Query("""
            SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b
            WHERE b.bookingDate BETWEEN :startDate AND :endDate
              AND b.status IN (com.example.football.enums.BookingStatus.CONFIRMED,
                               com.example.football.enums.BookingStatus.COMPLETED)
            """)
    BigDecimal calculateRevenueBetween(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    long countByStatus(BookingStatus status);
}

