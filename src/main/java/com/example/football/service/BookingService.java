package com.example.football.service;

import com.example.football.entity.*;
import com.example.football.enums.*;
import com.example.football.exception.SlotAlreadyBookedException;
import com.example.football.form.CounterBookingForm;
import com.example.football.form.CustomerBookingForm;
import com.example.football.integration.mail.EmailOtpService;
import com.example.football.integration.mail.VnPhoneUtils;
import com.example.football.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FootballFieldRepository fieldRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final FieldMaintenanceLogRepository maintenanceLogRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EmailOtpService emailOtpService;
    private final AuditLogService auditLogService;

    @Value("${booking.hold-minutes:5}")
    private int holdMinutes;

    /**
     * Lấy danh sách ID các khung giờ đang bị khóa (do đã có đơn PENDING_PAYMENT / CONFIRMED / COMPLETED
     * hoặc do Chủ sân khóa bảo trì theo ngày/ca trong bảng field_maintenance_logs).
     */
    @Transactional(readOnly = true)
    public List<Long> getBookedTimeSlotIds(Long fieldId, LocalDate bookingDate) {
        Set<Long> lockedIds = new HashSet<>(bookingRepository.findLockedTimeSlotIds(fieldId, bookingDate));

        List<FieldMaintenanceLog> maintLogs = maintenanceLogRepository.findByFieldIdAndDate(fieldId, bookingDate);
        if (!maintLogs.isEmpty()) {
            boolean fullDayLocked = maintLogs.stream().anyMatch(m -> m.getTimeSlot() == null);
            if (fullDayLocked) {
                timeSlotRepository.findAll().forEach(ts -> lockedIds.add(ts.getId()));
            } else {
                maintLogs.forEach(m -> {
                    if (m.getTimeSlot() != null) {
                        lockedIds.add(m.getTimeSlot().getId());
                    }
                });
            }
        }
        return new ArrayList<>(lockedIds);
    }

    /**
     * Luồng 1: Khách hàng đặt sân trực tuyến (ONLINE_CUSTOMER) - Bắt buộc thanh toán 100% qua VNPay.
     * - Kiểm tra chống giữ chỗ ảo: Mỗi tài khoản chỉ được phép có tối đa 1 đơn PENDING_PAYMENT chưa hết hạn.
     * - Giữ chỗ tối đa 5 phút (holdExpiresAt = now + 5 minutes).
     * - Khóa bi quan PESSIMISTIC_WRITE chống trùng lịch tuyệt đối.
     */
    @Transactional
    public PaymentTransaction createOnlineCustomerBooking(CustomerBookingForm form, String currentUsername) {
        if (form.getBookingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không thể đặt sân cho ngày trong quá khứ!");
        }

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản người dùng không hợp lệ!"));

        if (!user.isEnabled() || user.isCurrentlyLocked()) {
            throw new IllegalStateException("Tài khoản của bạn đang bị khóa do giữ chỗ quá hạn nhiều lần!");
        }

        // Quy tắc 3: Mỗi tài khoản chỉ được phép có tối đa 1 đơn đang PENDING_PAYMENT tại một thời điểm
        long activePendingCount = bookingRepository.countActivePendingBookingsByUser(user.getId(), LocalDateTime.now());
        if (activePendingCount >= 1) {
            throw new IllegalStateException(
                    "Bạn đang có 1 đơn đặt sân giữ chỗ chờ thanh toán! Vui lòng hoàn tất thanh toán đơn đó hoặc đợi hết 5 phút trước khi tạo đơn mới.");
        }

        // Khóa dòng sân bóng (PESSIMISTIC_WRITE)
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

        // Kiểm tra lịch bảo trì theo ngày/ca
        List<Long> lockedSlots = getBookedTimeSlotIds(field.getId(), form.getBookingDate());
        if (lockedSlots.contains(timeSlot.getId())) {
            throw new SlotAlreadyBookedException("Khung giờ này vừa có người đặt hoặc đang khóa bảo trì! Vui lòng chọn khung giờ khác.");
        }

        // Khóa bi quan trên bảng bookings
        List<Booking> conflictingBookings = bookingRepository.findActiveBookingsWithLock(
                field.getId(), timeSlot.getId(), form.getBookingDate());
        if (!conflictingBookings.isEmpty()) {
            throw new SlotAlreadyBookedException("Khung giờ này vừa có người đặt! Vui lòng chọn khung giờ khác.");
        }

        BigDecimal fieldPrice = field.getBasePricePerSlot();
        BigDecimal extraFee = timeSlot.getPeakHourExtraFee() != null ? timeSlot.getPeakHourExtraFee() : BigDecimal.ZERO;
        BigDecimal totalPrice = fieldPrice.add(extraFee);
        LocalDateTime now = LocalDateTime.now();

        Booking booking = Booking.builder()
                .bookingCode(generateUniqueBookingCode())
                .user(user)
                .customerName(user.getFullName())
                .customerPhone(user.getPhone())
                .customerEmail(user.getEmail())
                .bookingSource(BookingSource.ONLINE_CUSTOMER)
                .field(field)
                .timeSlot(timeSlot)
                .bookingDate(form.getBookingDate())
                .fieldPrice(fieldPrice)
                .extraFee(extraFee)
                .totalPrice(totalPrice)
                .paidAmount(BigDecimal.ZERO)
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .note(form.getNote() != null ? form.getNote().trim() : null)
                .holdExpiresAt(now.plusMinutes(holdMinutes))
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        String txnRef = savedBooking.getBookingCode() + "-T" + (System.currentTimeMillis() % 10000);
        PaymentChannel channel = form.getPaymentChannel() != null ? form.getPaymentChannel() : PaymentChannel.VNPAY;

        PaymentTransaction paymentTx = PaymentTransaction.builder()
                .booking(savedBooking)
                .txnRef(txnRef)
                .paymentChannel(channel)
                .amount(totalPrice)
                .status(TransactionStatus.PENDING)
                .build();

        PaymentTransaction savedTx = paymentTransactionRepository.save(paymentTx);

        log.info("[ONLINE BOOKING 100%] Tạo đơn giữ chỗ 5p mã={}, User='{}', Sân='{}', Ngày={}, Ca='{}', Tổng tiền 100%={}",
                savedBooking.getBookingCode(), currentUsername, field.getName(),
                savedBooking.getBookingDate(), timeSlot.getSlotName(), totalPrice);

        return savedTx;
    }

    /**
     * Luồng 2: Chủ sân đặt sân trực tiếp tại quầy (COUNTER_OWNER) và thu 100% tiền mặt (CASH_AT_COUNTER).
     */
    @Transactional
    public Booking createCounterBookingByOwner(CounterBookingForm form, String ownerUsername) {
        if (form.getBookingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không thể đặt sân cho ngày trong quá khứ!");
        }

        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản Chủ sân!"));

        FootballField field = fieldRepository.findByIdWithLock(form.getFieldId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng!"));

        if (field.getStatus() != FieldStatus.ACTIVE) {
            throw new IllegalStateException("Sân bóng '" + field.getName() + "' hiện không ở trạng thái hoạt động!");
        }

        TimeSlot timeSlot = timeSlotRepository.findById(form.getTimeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khung giờ đá!"));

        List<Booking> conflictingBookings = bookingRepository.findActiveBookingsWithLock(
                field.getId(), timeSlot.getId(), form.getBookingDate());
        if (!conflictingBookings.isEmpty()) {
            throw new SlotAlreadyBookedException("Khung giờ này đã có người đặt hoặc đang giữ chỗ! Vui lòng chọn ca khác.");
        }

        BigDecimal fieldPrice = field.getBasePricePerSlot();
        BigDecimal extraFee = timeSlot.getPeakHourExtraFee() != null ? timeSlot.getPeakHourExtraFee() : BigDecimal.ZERO;
        BigDecimal totalPrice = fieldPrice.add(extraFee);
        LocalDateTime now = LocalDateTime.now();

        Booking booking = Booking.builder()
                .bookingCode(generateUniqueBookingCode())
                .user(null) // Khách vãng lai đặt tại quầy
                .customerName(form.getCustomerName().trim())
                .customerPhone(VnPhoneUtils.normalize(form.getCustomerPhone()))
                .customerEmail(form.getCustomerEmail() != null && !form.getCustomerEmail().isBlank()
                        ? form.getCustomerEmail().trim() : null)
                .bookingSource(BookingSource.COUNTER_OWNER)
                .field(field)
                .timeSlot(timeSlot)
                .bookingDate(form.getBookingDate())
                .fieldPrice(fieldPrice)
                .extraFee(extraFee)
                .totalPrice(totalPrice)
                .paidAmount(totalPrice) // Thu 100% tiền mặt ngay tại quầy
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .note(form.getNote() != null && !form.getNote().isBlank()
                        ? "[Đặt tại quầy] " + form.getNote().trim()
                        : "[Đặt tại quầy - Thu 100% tiền mặt]")
                .holdExpiresAt(now.plusMinutes(holdMinutes))
                .createdByOwner(owner)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        PaymentTransaction cashTx = PaymentTransaction.builder()
                .booking(savedBooking)
                .txnRef(savedBooking.getBookingCode() + "-CASH")
                .providerTxnNo("CASH-" + System.currentTimeMillis())
                .paymentChannel(PaymentChannel.CASH_AT_COUNTER)
                .amount(totalPrice)
                .bankCode("CASH")
                .responseCode("00")
                .status(TransactionStatus.SUCCESS)
                .paidAt(now)
                .build();
        paymentTransactionRepository.save(cashTx);

        log.info("[COUNTER BOOKING 100%] Chủ sân '{}' tạo đơn tại quầy mã={}, Khách='{}', Đã thu 100%={}",
                ownerUsername, savedBooking.getBookingCode(), savedBooking.getCustomerName(), totalPrice);

        return savedBooking;
    }

    /**
     * Xem chi tiết đơn đặt sân có kiểm tra chính chủ (CHỐNG IDOR).
     */
    @Transactional(readOnly = true)
    public Booking getBookingForUserCheckingOwnership(Long bookingId, String currentUsername, String clientIp) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ!"));

        if (currentUser.getRole() != Role.ROLE_OWNER) {
            if (booking.getUser() == null || !booking.getUser().getUsername().equals(currentUsername)) {
                auditLogService.logSecurityEvent(
                        currentUser.getId(),
                        currentUsername,
                        SecurityEventType.IDOR_ATTEMPT_BLOCKED,
                        "/booking/result/" + bookingId,
                        clientIp,
                        "Khách hàng cố tình xem đơn đặt sân ID=" + bookingId + " của người khác!"
                );
                throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn đặt sân của người khác!");
            }
        }

        return booking;
    }

    /**
     * Tạo mới giao dịch thanh toán VNPay cho đơn đang ở trạng thái PENDING_PAYMENT (chưa quá hạn 5 phút).
     */
    @Transactional
    public PaymentTransaction createRetryPaymentTransaction(Long bookingId, String currentUsername, String clientIp) {
        Booking booking = getBookingForUserCheckingOwnership(bookingId, currentUsername, clientIp);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Đơn đặt sân này không còn ở trạng thái chờ thanh toán!");
        }
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            booking.setStatus(BookingStatus.EXPIRED_UNPAID);
            booking.setPaymentStatus(PaymentStatus.UNPAID);
            bookingRepository.save(booking);
            throw new IllegalStateException("Đơn đặt sân đã quá hạn giữ chỗ 5 phút, vui lòng đặt lại!");
        }

        String txnRef = booking.getBookingCode() + "-T" + (System.currentTimeMillis() % 10000);
        PaymentTransaction paymentTx = PaymentTransaction.builder()
                .booking(booking)
                .txnRef(txnRef)
                .paymentChannel(PaymentChannel.VNPAY)
                .amount(booking.getTotalPrice())
                .status(TransactionStatus.PENDING)
                .build();
        return paymentTransactionRepository.save(paymentTx);
    }

    /**
     * Luồng 4: Khách hàng bấm hủy đơn (STRICT NON-REFUNDABLE - KHÔNG HOÀN TIỀN DƯỚI MỌI HÌNH THỨC).
     * - Nếu đơn đã CONFIRMED (đã trả 100%):
     *   + Đổi status -> CANCELLED_NO_REFUND (cột active_slot_lock tự động về NULL -> mở khóa sân ngay lập tức).
     *   + Đổi paymentStatus -> FORFEITED (Khách mất 100% tiền đã trả, giữ nguyên paidAmount trong doanh thu Chủ sân).
     * - Nếu đơn đang PENDING_PAYMENT (chưa trả tiền):
     *   + Đổi status -> CANCELLED_NO_REFUND, paymentStatus -> UNPAID.
     */
    @Transactional
    public void cancelBookingByCustomer(Long bookingId, String currentUsername, String cancelReason, String clientIp) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ!"));

        // CHỐT CHẶN CHỐNG IDOR BẮT BUỘC
        if (booking.getUser() == null || !booking.getUser().getUsername().equals(currentUsername)) {
            auditLogService.logSecurityEvent(
                    currentUser.getId(),
                    currentUsername,
                    SecurityEventType.IDOR_ATTEMPT_BLOCKED,
                    "/booking/cancel/" + bookingId,
                    clientIp,
                    "Khách hàng cố tình hủy đơn đặt sân ID=" + bookingId + " của người khác!"
            );
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn đặt sân của người khác!");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Đơn đặt sân này không ở trạng thái có thể hủy!");
        }

        boolean wasConfirmedAndPaid = (booking.getStatus() == BookingStatus.CONFIRMED
                && booking.getPaymentStatus() == PaymentStatus.PAID);

        booking.setStatus(BookingStatus.CANCELLED_NO_REFUND);
        if (wasConfirmedAndPaid) {
            // Khách mất 100% tiền đã trả, giữ nguyên paidAmount trong doanh thu Chủ sân
            booking.setPaymentStatus(PaymentStatus.FORFEITED);
        }
        booking.setCancelledBy(currentUser);
        booking.setCancelReason(cancelReason != null && !cancelReason.isBlank()
                ? cancelReason.trim()
                : "Khách hàng chủ động hủy lịch (Đồng ý quy định không hoàn tiền)");

        bookingRepository.save(booking);
        log.info("[CUSTOMER CANCEL NO-REFUND] Đơn {} đã chuyển sang CANCELLED_NO_REFUND, paymentStatus={}, mở khóa sân ngay lập tức!",
                booking.getBookingCode(), booking.getPaymentStatus());
    }

    /**
     * Chủ sân cập nhật trạng thái đơn:
     * - COMPLETED (Hoàn thành ca đá)
     * - NO_SHOW (Khách bỏ sân không đến -> paymentStatus chuyển sang FORFEITED, mở khóa sân)
     * - CANCELLED_NO_REFUND (Hủy không hoàn tiền -> paymentStatus chuyển sang FORFEITED nếu đã trả tiền, mở khóa sân)
     */
    @Transactional
    public void updateBookingStatusByOwner(Long bookingId, BookingStatus newStatus, String ownerUsername) {
        updateBookingStatusByOwner(bookingId, newStatus, ownerUsername, null);
    }

    @Transactional
    public void updateBookingStatusByOwner(Long bookingId, BookingStatus newStatus, String ownerUsername, String customReason) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));
        User owner = (ownerUsername != null && !ownerUsername.isBlank())
                ? userRepository.findByUsername(ownerUsername).orElse(null)
                : null;

        if (newStatus == BookingStatus.NO_SHOW || newStatus == BookingStatus.CANCELLED_NO_REFUND) {
            if (booking.getPaymentStatus() == PaymentStatus.PAID) {
                booking.setPaymentStatus(PaymentStatus.FORFEITED);
            }
            booking.setCancelledBy(owner);
            if (customReason != null && !customReason.isBlank()) {
                booking.setCancelReason(customReason.trim());
            } else {
                booking.setCancelReason(newStatus == BookingStatus.NO_SHOW
                        ? "Khách bỏ sân không đến (NO_SHOW - Không hoàn tiền)"
                        : "Chủ sân hủy đơn theo yêu cầu (Không hoàn tiền)");
            }
        } else if (newStatus == BookingStatus.COMPLETED) {
            booking.setPaymentStatus(PaymentStatus.PAID);
            booking.setPaidAmount(booking.getTotalPrice());
        }

        booking.setStatus(newStatus);
        bookingRepository.save(booking);
        log.info("[OWNER UPDATE BOOKING] Chủ sân '{}' cập nhật đơn {} -> status={}, paymentStatus={}",
                ownerUsername, booking.getBookingCode(), newStatus, booking.getPaymentStatus());
    }

    /**
     * Xử lý kết quả trả về từ cổng thanh toán VNPay Sandbox (100% giá trị đơn).
     */
    @Transactional
    public Booking processVnPayCallback(Map<String, String> vnpParams) {
        String txnRef = vnpParams.get("vnp_TxnRef");
        String responseCode = vnpParams.get("vnp_ResponseCode");
        String providerTxnNo = vnpParams.getOrDefault("vnp_TransactionNo", "VNP-" + System.currentTimeMillis());
        String bankCode = vnpParams.getOrDefault("vnp_BankCode", "NCB");
        String secureHash = vnpParams.get("vnp_SecureHash");

        PaymentTransaction tx = paymentTransactionRepository.findByTxnRefWithBooking(txnRef)
                .orElseGet(() -> {
                    // Fallback nếu vnp_TxnRef truyền thẳng mã bookingCode
                    Booking b = bookingRepository.findByBookingCode(txnRef)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch với mã tham chiếu: " + txnRef));
                    return PaymentTransaction.builder()
                            .booking(b)
                            .txnRef(txnRef)
                            .paymentChannel(PaymentChannel.VNPAY)
                            .amount(b.getTotalPrice())
                            .status(TransactionStatus.PENDING)
                            .build();
                });

        Booking booking = tx.getBooking();

        // Chống xử lý trùng lặp nếu đơn đã CONFIRMED
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getPaymentStatus() == PaymentStatus.PAID) {
            return booking;
        }

        // Kiểm tra nếu đơn đã bị quá hạn 5 phút (EXPIRED_UNPAID)
        if (booking.getStatus() == BookingStatus.EXPIRED_UNPAID) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setResponseCode(responseCode);
            paymentTransactionRepository.save(tx);
            throw new IllegalStateException("Đơn đặt sân " + booking.getBookingCode() + " đã quá thời hạn giữ chỗ 5 phút và bị hủy tự động!");
        }

        boolean isSuccess = "00".equals(responseCode);

        tx.setProviderTxnNo(providerTxnNo);
        tx.setBankCode(bankCode);
        tx.setResponseCode(responseCode);
        tx.setSecureHash(secureHash);
        tx.setStatus(isSuccess ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        if (isSuccess) {
            tx.setPaidAt(LocalDateTime.now());
        }
        paymentTransactionRepository.save(tx);

        if (isSuccess) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPaymentStatus(PaymentStatus.PAID);
            booking.setPaidAmount(booking.getTotalPrice());
            Booking savedBooking = bookingRepository.save(booking);

            // Gửi Email biên lai xác nhận đặt sân 100%
            emailOtpService.sendBookingConfirmationEmailAsync(savedBooking);

            log.info("[VNPAY 100% SUCCESS] Đơn {} đã thanh toán 100% ({} VNĐ), mã GD VNPay={}",
                    savedBooking.getBookingCode(), savedBooking.getPaidAmount(), providerTxnNo);
            return savedBooking;
        } else {
            // Nếu khách bấm hủy ngay trên cổng VNPay -> Giải phóng khung giờ ngay lập tức
            booking.setStatus(BookingStatus.EXPIRED_UNPAID);
            booking.setPaymentStatus(PaymentStatus.UNPAID);
            booking.setCancelReason("Khách hủy giao dịch trên cổng VNPay (Mã lỗi: " + responseCode + ")");
            Booking savedBooking = bookingRepository.save(booking);
            log.warn("[VNPAY FAILED/CANCELLED] Đơn {} không thanh toán thành công (ResponseCode={}), nhả khóa sân!",
                    savedBooking.getBookingCode(), responseCode);
            return savedBooking;
        }
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
    public BigDecimal getTotalForfeitedRevenue() {
        return bookingRepository.calculateTotalForfeitedRevenue();
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
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
            String candidate = "BK" + datePart + "-" + randomPart;
            if (bookingRepository.findByBookingCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "BK" + System.currentTimeMillis();
    }
}
