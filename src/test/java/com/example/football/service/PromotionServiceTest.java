package com.example.football.service;

import com.example.football.entity.*;
import com.example.football.enums.DiscountType;
import com.example.football.repository.*;
import jakarta.servlet.http.HttpSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PromotionServiceTest {
    private PromotionRepository promotionRepository;
    private PromotionVersionRepository versionRepository;
    private PromotionReservationRepository reservationRepository;
    private BookingRepository bookingRepository;
    private FootballFieldRepository fieldRepository;
    private TimeSlotRepository timeSlotRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuditLogService auditLogService;
    private PromotionService service;

    @BeforeEach
    void setUp() {
        promotionRepository = mock(PromotionRepository.class);
        versionRepository = mock(PromotionVersionRepository.class);
        reservationRepository = mock(PromotionReservationRepository.class);
        bookingRepository = mock(BookingRepository.class);
        fieldRepository = mock(FootballFieldRepository.class);
        timeSlotRepository = mock(TimeSlotRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditLogService = mock(AuditLogService.class);
        service = new PromotionService(promotionRepository, versionRepository, reservationRepository,
                bookingRepository, fieldRepository, timeSlotRepository, userRepository, passwordEncoder,
                auditLogService);
    }

    @Test
    void percentageDiscountUsesOnlyOverlapAndRoundsToWholeDong() {
        User owner = User.builder().id(1L).build();
        Promotion promotion = promotion(owner, DiscountType.PERCENTAGE, "50", "07:00", "08:00");
        FootballField field = FootballField.builder().id(4L).createdBy(owner)
                .basePricePerSlot(new BigDecimal("120000")).build();
        TimeSlot slot = TimeSlot.builder().startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(8, 0)).peakHourExtraFee(BigDecimal.ZERO).build();
        mockPreview(promotion, field, slot);

        var result = service.previewPromotion(4L, LocalDate.now(PromotionService.VN_ZONE),
                slot.getStartTime(), slot.getEndTime(), " promo ", null);

        assertTrue(result.isApplicable());
        assertEquals(new BigDecimal("30000"), result.getDiscountAmount());
        assertEquals(new BigDecimal("90000"), result.getTotalAmount());
    }

    @Test
    void discountCannotReduceBookingBelowOneDong() {
        User owner = User.builder().id(1L).build();
        Promotion promotion = promotion(owner, DiscountType.FIXED_AMOUNT, "500", "06:00", "07:00");
        FootballField field = FootballField.builder().id(4L).createdBy(owner)
                .basePricePerSlot(BigDecimal.ONE).build();
        TimeSlot slot = TimeSlot.builder().startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 0)).peakHourExtraFee(BigDecimal.ZERO).build();
        mockPreview(promotion, field, slot);

        var result = service.previewPromotion(4L, LocalDate.now(PromotionService.VN_ZONE),
                slot.getStartTime(), slot.getEndTime(), "PROMO", null);

        assertTrue(result.isApplicable());
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(BigDecimal.ONE, result.getTotalAmount());
    }

    @Test
    void reservationChecksCapacityAfterAcquiringPromotionWriteLock() {
        User owner = User.builder().id(1L).build();
        Promotion promotion = promotion(owner, DiscountType.FIXED_AMOUNT, "1000", "06:00", "07:00");
        promotion.setMaxTotalUses(1);
        promotion.setReservedCount(1);
        FootballField field = FootballField.builder().id(4L).createdBy(owner)
                .basePricePerSlot(new BigDecimal("10000")).build();
        TimeSlot slot = TimeSlot.builder().startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 0)).peakHourExtraFee(BigDecimal.ZERO).build();
        mockPreview(promotion, field, slot);
        when(promotionRepository.findByCodeWithLock("PROMO")).thenReturn(Optional.of(promotion));
        Booking booking = Booking.builder().id(9L).holdExpiresAt(LocalDateTime.now(PromotionService.VN_ZONE).plusMinutes(5)).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.reserveForBooking(4L, LocalDate.now(PromotionService.VN_ZONE),
                        slot.getStartTime(), slot.getEndTime(), "PROMO", null, booking));

        assertEquals("Mã khuyến mại đã hết lượt sử dụng", error.getMessage());
        verify(promotionRepository).findByCodeWithLock("PROMO");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void promotionLookupForReservationUsesPessimisticWriteLock() throws Exception {
        Lock lock = PromotionRepository.class
                .getMethod("findByCodeWithLock", String.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void releasingReservationIsIdempotentAndReturnsCapacityOnce() {
        Promotion promotion = Promotion.builder().id(3L).reservedCount(1).usedCount(0).build();
        PromotionReservation reservation = PromotionReservation.builder().id(7L).promotionId(3L)
                .status(PromotionReservation.ReservationStatus.RESERVED).build();
        when(reservationRepository.findByBookingId(20L)).thenReturn(Optional.of(reservation));
        when(promotionRepository.findByIdWithLock(3L)).thenReturn(Optional.of(promotion));
        when(reservationRepository.findByIdWithLock(7L)).thenReturn(Optional.of(reservation));

        service.releaseReservation(20L);
        service.releaseReservation(20L);

        assertEquals(PromotionReservation.ReservationStatus.RELEASED, reservation.getStatus());
        assertEquals(0, promotion.getReservedCount());
        assertEquals(0, promotion.getUsedCount());
        verify(promotionRepository, times(1)).save(promotion);
    }

    @Test
    void reauthenticationExpiresAfterFiveMinutes() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(PromotionService.PROMO_REAUTH_AT))
                .thenReturn(LocalDateTime.now(PromotionService.VN_ZONE).minusMinutes(5));

        assertFalse(service.isReauthenticated(session));
    }

    @Test
    void successfulReauthenticationStoresVietnameseLocalTimestamp() {
        HttpSession session = mock(HttpSession.class);
        User owner = User.builder().username("owner").password("encoded").build();
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("current", "encoded")).thenReturn(true);

        assertTrue(service.reauthenticate("owner", "current", session));

        verify(session).setAttribute(eq(PromotionService.PROMO_REAUTH_AT), any(LocalDateTime.class));
    }

    @Test
    void sensitiveActionsRejectExpiredReauthentication() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(PromotionService.PROMO_REAUTH_AT))
                .thenReturn(LocalDateTime.now(PromotionService.VN_ZONE).minusMinutes(6));

        assertThrows(IllegalStateException.class,
                () -> service.requireReauthentication(session, "owner"));
        verify(auditLogService).logEvent(eq("owner"), any(), anyString(), anyString(), anyString());
    }

    private Promotion promotion(User owner, DiscountType type, String value, String start, String end) {
        LocalDateTime now = LocalDateTime.now(PromotionService.VN_ZONE);
        return Promotion.builder().id(2L).code("PROMO").owner(owner).discountType(type)
                .discountValue(new BigDecimal(value)).startAt(now.minusDays(1)).endAt(now.plusDays(1))
                .applicableDays(LocalDate.now(PromotionService.VN_ZONE).getDayOfWeek().name())
                .discountStartTime(LocalTime.parse(start)).discountEndTime(LocalTime.parse(end))
                .usedCount(0).reservedCount(0).stopped(false).currentVersion(1).build();
    }

    private void mockPreview(Promotion promotion, FootballField field, TimeSlot slot) {
        when(promotionRepository.findByCode("PROMO")).thenReturn(Optional.of(promotion));
        when(fieldRepository.findById(4L)).thenReturn(Optional.of(field));
        when(timeSlotRepository.findAllByOrderByStartTimeAsc()).thenReturn(List.of(slot));
    }
}
