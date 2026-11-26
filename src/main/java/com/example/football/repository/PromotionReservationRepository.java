package com.example.football.repository;

import com.example.football.entity.PromotionReservation;
import com.example.football.entity.PromotionReservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionReservationRepository extends JpaRepository<PromotionReservation, Long> {
    long countByPromotionIdAndStatus(Long promotionId, ReservationStatus status);

    @Query("""
        SELECT COUNT(r) FROM PromotionReservation r
        WHERE r.promotionId = :promotionId AND r.userId = :userId
          AND r.status IN (com.example.football.entity.PromotionReservation.ReservationStatus.RESERVED,
                           com.example.football.entity.PromotionReservation.ReservationStatus.CONFIRMED)
        """)
    long countUserUses(@Param("promotionId") Long promotionId, @Param("userId") Long userId);

    Optional<PromotionReservation> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PromotionReservation r WHERE r.id = :id")
    Optional<PromotionReservation> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT r FROM PromotionReservation r WHERE r.status = com.example.football.entity.PromotionReservation.ReservationStatus.RESERVED AND r.expiresAt <= :now")
    List<PromotionReservation> findExpiredReserved();

    boolean existsByPromotionId(Long promotionId);
}
