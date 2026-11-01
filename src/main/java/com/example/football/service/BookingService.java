package com.example.football.service;

import com.example.football.entity.*;
import com.example.football.enums.BookingStatus;
import com.example.football.enums.FieldStatus;
import com.example.football.enums.PaymentStatus;
import com.example.football.enums.Role;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.BookingForm;
import com.example.football.repository.BookingRepository;
import com.example.football.repository.FootballFieldRepository;
import com.example.football.repository.PaymentTransactionRepository;
import com.example.football.repository.TimeSlotRepository;
import com.example.football.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FootballFieldRepository fieldRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(readOnly = true)
    public List<Long> getBookedTimeSlotIds(Long fieldId, LocalDate bookingDate) {
        return bookingRepository.findBookedTimeSlotIds(fieldId, bookingDate);
    }

    /**
     * Đặt sân trực tuyến cho Khách hàng.
     * - Sử dụng khóa bi quan kép (@Lock(LockModeType.PESSIMISTIC_WRITE) trên cả FootballField và Booking)
     *   để đảm bảo tuần tự hóa 100% ngay cả khi 2 khách bấm cùng 1 mili-giây vào khung giờ trống đầu tiên.
     * - Tính tổng tiền: totalPrice = field.pricePerHour + timeSlot.peakHourExtraFee.
     */
    @Transactional
    public Booking createCustomerBooking(BookingForm form, String currentUsername) {
        if (form.getBookingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không thể đặt sân cho ngày trong quá khứ!");
        }

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản người dùng không hợp lệ!"));

        // Khóa dòng sân bóng (SELECT ... FOR UPDATE) để chống Race Condition khi insert dòng mới
        FootballField field = fieldRepository.findByIdWithLock(form.getFieldId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng!"));

        if (field.getStatus() != FieldStatus.ACTIVE) {
            throw new IllegalStateException("Sân bóng '" + field.getName() + "' hiện đang bảo trì hoặc tạm ngưng nhận lịch!");
        }

        TimeSlot timeSlot = timeSlotRepository.findById(form.getTimeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khung giờ đá!"));

        if (!timeSlot.isActive()) {
            throw new IllegalStateException("Khung giờ này hiện đang tạm ngưng phục vụ!");
        }

        // KHÓA BI QUAN PESSIMISTIC_WRITE CHỐNG TRÙNG LỊCH (RACE CONDITION)
        List<Booking> conflictingBookings = bookingRepository.findActiveBookingsWithLock(
                field.getId(), timeSlot.getId(), form.getBookingDate());

        if (!conflictingBookings.isEmpty()) {
            throw new SlotAlreadyBookedException("Khung giờ này vừa có người đặt! Vui lòng chọn khung giờ khác.");
        }

        BigDecimal totalPrice = field.getPricePerHour().add(timeSlot.getPeakHourExtraFee());
        String paymentMethod = "VNPAY".equalsIgnoreCase(form.getPaymentMethod()) ? "VNPAY" : "CASH";
        BookingStatus initialStatus = "VNPAY".equals(paymentMethod)
                ? BookingStatus.PENDING_PAYMENT
                : BookingStatus.PENDING_APPROVAL;

        Booking booking = Booking.builder()
                .bookingCode(generateUniqueBookingCode())
                .user(user)
                .field(field)
                .timeSlot(timeSlot)
                .bookingDate(form.getBookingDate())
                .totalPrice(totalPrice)
                .status(initialStatus)
                .paymentMethod(paymentMethod)
                .customerName(user.getFullName())
                .customerPhone(user.getPhone())
                .note(form.getNote() != null ? form.getNote().trim() : null)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.info("[BOOKING CREATED] Mã đơn={}, User='{}', Sân='{}', Ngày={}, Ca='{}', Tổng tiền={}, Trạng thái={}",
                savedBooking.getBookingCode(), currentUsername, field.getName(),
                savedBooking.getBookingDate(), timeSlot.getSlotName(), totalPrice, initialStatus);

        return savedBooking;
    }

    /**
     * Tạo đơn đặt sân trực tiếp tại quầy dành riêng cho Chủ sân (ROLE_OWNER).
     */
    @Transactional
    public Booking createWalkInBookingByOwner(BookingForm form, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản Chủ sân!"));

        FootballField field = fieldRepository.findByIdWithLock(form.getFieldId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng!"));

        TimeSlot timeSlot = timeSlotRepository.findById(form.getTimeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khung giờ đá!"));

        List<Booking> conflictingBookings = bookingRepository.findActiveBookingsWithLock(
                field.getId(), timeSlot.getId(), form.getBookingDate());

        if (!conflictingBookings.isEmpty()) {
            throw new SlotAlreadyBookedException("Khung giờ này đã có đơn đặt! Vui lòng chọn ca khác.");
        }

        BigDecimal totalPrice = field.getPricePerHour().add(timeSlot.getPeakHourExtraFee());
        String walkInName = (form.getCustomerName() != null && !form.getCustomerName().isBlank())
                ? form.getCustomerName().trim()
                : "Khách vãng lai tại quầy";
        String walkInPhone = (form.getCustomerPhone() != null && !form.getCustomerPhone().isBlank())
                ? form.getCustomerPhone().trim()
                : owner.getPhone();

        Booking booking = Booking.builder()
                .bookingCode(generateUniqueBookingCode())
                .user(owner)
                .field(field)
                .timeSlot(timeSlot)
                .bookingDate(form.getBookingDate())
                .totalPrice(totalPrice)
                .status(BookingStatus.CONFIRMED)
                .paymentMethod("COUNTER_CASH")
                .customerName(walkInName)
                .customerPhone(walkInPhone)
                .note(form.getNote() != null ? "[Đặt tại quầy] " + form.getNote().trim() : "[Đặt trực tiếp tại quầy]")
                .build();

        Booking saved = bookingRepository.save(booking);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .booking(saved)
                .transactionNo("COUNTER-" + saved.getBookingCode())
                .amount(totalPrice)
                .paymentMethod("COUNTER_CASH")
                .paymentStatus(PaymentStatus.PAID)
                .responseCode("00")
                .bankCode("CASH")
                .paidAt(LocalDateTime.now())
                .build();
        paymentTransactionRepository.save(transaction);

        log.info("[OWNER COUNTER BOOKING] Chủ sân '{}' tạo đơn tại quầy mã {} cho khách '{}'",
                ownerUsername, saved.getBookingCode(), walkInName);

        return saved;
    }

    /**
     * Xem chi tiết đơn đặt sân có kiểm tra chính chủ (CHỐNG IDOR).
     */
    @Transactional(readOnly = true)
    public Booking getBookingForUserCheckingOwnership(Long bookingId, String currentUsername) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ!"));

        // Nếu không phải Chủ sân thì bắt buộc kiểm tra chính chủ chống IDOR
        if (currentUser.getRole() != Role.ROLE_OWNER
                && !booking.getUser().getUsername().equals(currentUsername)) {
            log.warn("[SECURITY IDOR BLOCKED] User '{}' cố tình xem đơn đặt sân ID={} của người dùng '{}'!",
                    currentUsername, bookingId, booking.getUser().getUsername());
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn đặt sân của người khác!");
        }

        return booking;
    }

    /**
     * Khách hàng tự hủy đơn đặt sân của chính mình (CHỐNG IDOR).
     */
    @Transactional
    public void cancelBookingByCustomer(Long bookingId, String currentUsername) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));

        // CHỐT CHẶN CHỐNG IDOR BẮT BUỘC THEO YÊU CẦU BẢO MẬT
        if (!booking.getUser().getUsername().equals(currentUsername)) {
            log.warn("[SECURITY IDOR BLOCKED] User '{}' cố tình hủy đơn đặt sân ID={} của '{}'!",
                    currentUsername, bookingId, booking.getUser().getUsername());
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn đặt sân của người khác!");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Đơn đặt sân này đã được hủy trước đó!");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Không thể hủy đơn đặt sân đã hoàn thành!");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("[CUSTOMER CANCEL BOOKING] Khách hàng '{}' đã hủy đơn mã {}", currentUsername, booking.getBookingCode());
    }

    /**
     * Chủ sân duyệt hoặc hủy đơn đặt sân của khách.
     */
    @Transactional
    public void updateBookingStatusByOwner(Long bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));
        booking.setStatus(newStatus);
        bookingRepository.save(booking);
        log.info("[OWNER UPDATE BOOKING] Đơn {} chuyển sang trạng thái {}", booking.getBookingCode(), newStatus);
    }

    /**
     * Xử lý kết quả trả về từ cổng thanh toán VNPay Sandbox.
     */
    @Transactional
    public Booking processVnPayCallback(Map<String, String> vnpParams) {
        String bookingCode = vnpParams.get("vnp_TxnRef");
        String responseCode = vnpParams.get("vnp_ResponseCode");
        String transactionNo = vnpParams.getOrDefault("vnp_TransactionNo", "VNP-" + System.currentTimeMillis());
        String bankCode = vnpParams.getOrDefault("vnp_BankCode", "NCB");

        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân với mã: " + bookingCode));

        // Chống xử lý trùng lặp nếu đơn đã CONFIRMED trước đó
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return booking;
        }

        boolean isSuccess = "00".equals(responseCode);

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .booking(booking)
                .transactionNo(transactionNo)
                .amount(booking.getTotalPrice())
                .paymentMethod("VNPAY")
                .paymentStatus(isSuccess ? PaymentStatus.PAID : PaymentStatus.FAILED)
                .responseCode(responseCode)
                .bankCode(bankCode)
                .paidAt(isSuccess ? LocalDateTime.now() : null)
                .build();
        paymentTransactionRepository.save(paymentTransaction);

        if (isSuccess) {
            booking.setStatus(BookingStatus.CONFIRMED);
            log.info("[VNPAY PAYMENT SUCCESS] Đơn {} đã thanh toán thành công, mã GD VNPay={}", bookingCode, transactionNo);
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
            log.warn("[VNPAY PAYMENT FAILED] Đơn {} thanh toán thất bại/hủy (ResponseCode={}), mở lại khung giờ!", bookingCode, responseCode);
        }

        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsByUsername(String username) {
        return bookingRepository.findByUsernameWithDetails(username);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForOwner(LocalDate date, BookingStatus status) {
        return bookingRepository.findAllForOwner(date, status);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDailyRevenue(LocalDate date) {
        return bookingRepository.calculateDailyRevenue(date);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyRevenue(LocalDate dateInMonth) {
        LocalDate startOfMonth = dateInMonth.withDayOfMonth(1);
        LocalDate endOfMonth = dateInMonth.withDayOfMonth(dateInMonth.lengthOfMonth());
        return bookingRepository.calculateRevenueBetween(startOfMonth, endOfMonth);
    }

    @Transactional(readOnly = true)
    public long countBookingsByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countTotalBookings() {
        return bookingRepository.count();
    }

    private String generateUniqueBookingCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
            String candidate = "FB" + datePart + randomPart;
            if (bookingRepository.findByBookingCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "FB" + System.currentTimeMillis();
    }
}
