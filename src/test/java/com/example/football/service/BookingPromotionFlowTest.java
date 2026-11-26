package com.example.football.service;

import com.example.football.entity.*;
import com.example.football.enums.*;
import com.example.football.form.CustomerBookingForm;
import com.example.football.form.PromotionPreviewResult;
import com.example.football.integration.mail.EmailOtpService;
import com.example.football.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingPromotionFlowTest {
    @Test
    void onlineBookingPersistsFinalReservedPriceSnapshotAndVnpayAmount() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        FootballFieldRepository fieldRepository = mock(FootballFieldRepository.class);
        TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
        FieldMaintenanceLogRepository maintenanceRepository = mock(FieldMaintenanceLogRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        EmailOtpService emailService = mock(EmailOtpService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        PromotionService promotionService = mock(PromotionService.class);

        User user = User.builder().id(5L).username("customer").fullName("Test")
                .phone("0900000000").email("test@example.com").enabled(true).build();
        FootballField field = FootballField.builder().id(3L).name("Field")
                .status(FieldStatus.ACTIVE).basePricePerSlot(new BigDecimal("100000")).build();
        TimeSlot slot = TimeSlot.builder().id(2L).slotName("Slot")
                .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(7, 30))
                .peakHourExtraFee(new BigDecimal("10000")).active(true).build();
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(bookingRepository.countActivePendingBookingsByUser(eq(5L), any())).thenReturn(0L);
        when(fieldRepository.findByIdWithLock(3L)).thenReturn(Optional.of(field));
        when(timeSlotRepository.findById(2L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findLockedTimeSlotIds(eq(3L), any(LocalDate.class))).thenReturn(List.of());
        when(maintenanceRepository.findByFieldIdAndDate(eq(3L), any(LocalDate.class))).thenReturn(List.of());
        when(bookingRepository.findActiveBookingsWithLock(eq(3L), eq(2L), any())).thenReturn(List.of());
        when(bookingRepository.findByBookingCode(anyString())).thenReturn(Optional.empty());

        PromotionPreviewResult preview = new PromotionPreviewResult(true, "ok", "PROMO",
                new BigDecimal("110000"), new BigDecimal("10000"),
                new BigDecimal("100000"), 2);
        PromotionPreviewResult reserved = new PromotionPreviewResult(true, "ok", "PROMO",
                new BigDecimal("110000"), new BigDecimal("25000"),
                new BigDecimal("85000"), 3);
        when(promotionService.previewPromotion(eq(3L), any(), any(), any(), eq("PROMO"), eq(user)))
                .thenReturn(preview);
        when(promotionService.reserveForBooking(eq(3L), any(), any(), any(), eq("PROMO"), eq(user), any()))
                .thenReturn(reserved);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingService service = new BookingService(bookingRepository, fieldRepository, timeSlotRepository,
                maintenanceRepository, userRepository, paymentRepository, emailService, auditLogService,
                promotionService);
        CustomerBookingForm form = CustomerBookingForm.builder().fieldId(3L)
                .bookingDate(LocalDate.now(PromotionService.VN_ZONE).plusDays(1))
                .timeSlotId(2L).promoCode("PROMO").build();

        PaymentTransaction transaction = service.createOnlineCustomerBooking(form, "customer");

        assertEquals(new BigDecimal("110000"), transaction.getBooking().getOriginalAmount());
        assertEquals(new BigDecimal("25000"), transaction.getBooking().getDiscountAmount());
        assertEquals(new BigDecimal("85000"), transaction.getBooking().getTotalPrice());
        assertEquals("PROMO", transaction.getBooking().getPromotionCode());
        assertEquals(3, transaction.getBooking().getPromotionVersion());
        assertEquals(new BigDecimal("85000"), transaction.getAmount());
    }

    @Test
    void cleanupOfExpiredHoldReleasesReservedPromotion() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        PromotionService promotionService = mock(PromotionService.class);
        Booking booking = Booking.builder().id(12L).bookingCode("B-12")
                .status(BookingStatus.PENDING_PAYMENT)
                .holdExpiresAt(LocalDateTime.now(PromotionService.VN_ZONE).minusMinutes(1))
                .field(FootballField.builder().name("Field").build())
                .timeSlot(TimeSlot.builder().slotName("Slot").build())
                .build();
        when(bookingRepository.findExpiredPendingBookingsWithLock(any())).thenReturn(List.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingCleanupScheduler scheduler = new BookingCleanupScheduler(bookingRepository, userRepository,
                auditLogService, promotionService);
        scheduler.cleanupExpiredPendingBookings();

        assertEquals(BookingStatus.EXPIRED_UNPAID, booking.getStatus());
        verify(promotionService).releaseReservation(12L);
    }
}
