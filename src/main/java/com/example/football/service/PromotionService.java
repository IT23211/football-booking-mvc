package com.example.football.service;

import com.example.football.entity.*;
import com.example.football.enums.*;
import com.example.football.form.PromotionForm;
import com.example.football.form.PromotionPreviewResult;
import com.example.football.repository.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {
    public static final String PROMO_REAUTH_AT = "PROMO_REAUTH_AT";
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PromotionRepository promotionRepository;
    private final PromotionVersionRepository promotionVersionRepository;
    private final PromotionReservationRepository promotionReservationRepository;
    private final BookingRepository bookingRepository;
    private final FootballFieldRepository fieldRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Promotion> searchOwnerPromotions(Long ownerId, String keyword, PromotionDisplayStatus status) {
        List<Promotion> promotions = promotionRepository.searchOwnerPromotions(ownerId, keyword);
        if (status == null) return promotions;
        return promotions.stream()
                .filter(p -> p.getDisplayStatus() == status)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Promotion> getPublicPromotionsForField(Long fieldId) {
        FootballField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng"));
        if (field.getCreatedBy() == null) return List.of();
        return promotionRepository.findPublicPromotionsByOwner(field.getCreatedBy().getId())
                .stream().filter(p -> p.getDisplayStatus() == PromotionDisplayStatus.ACTIVE
                        || p.getDisplayStatus() == PromotionDisplayStatus.UPCOMING)
                .toList();
    }

    @Transactional(readOnly = true)
    public Promotion getOwnerPromotion(Long ownerId, Long promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khuyến mại"));
        if (!promotion.getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác khuyến mại thuộc cơ sở này");
        }
        return promotion;
    }

    public boolean isReauthenticated(HttpSession session) {
        Object value = session.getAttribute(PROMO_REAUTH_AT);
        if (!(value instanceof LocalDateTime)) return false;
        return ((LocalDateTime) value).isAfter(LocalDateTime.now(VN_ZONE).minusMinutes(5));
    }

    public boolean reauthenticate(String username, String password, HttpSession session) {
        if (password == null || password.isBlank()) return false;
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không hợp lệ"));
        if (!passwordEncoder.matches(password, user.getPassword())) return false;
        session.setAttribute(PROMO_REAUTH_AT, LocalDateTime.now(VN_ZONE));
        return true;
    }

    public void requireReauthentication(HttpSession session, String username) {
        if (!isReauthenticated(session)) {
            auditLogService.logEvent(username, SecurityEventType.PROMOTION_REAUTH_FAILED,
                    "WEB", "/owner/promotions", "Yêu cầu xác thực lại mật khẩu quản trị trong 5 phút");
            throw new IllegalStateException("Vui lòng xác thực lại mật khẩu quản trị trong vòng 5 phút");
        }
    }

    @Transactional
    public Promotion createPromotion(PromotionForm form, String ownerUsername, HttpSession session) {
        requireReauthentication(session, ownerUsername);
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Chủ sân"));
        validateForm(form, null);
        String code = normalizeCode(form.getCode());
        if (promotionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã khuyến mại đã tồn tại, kể cả chương trình đã ngừng");
        }

        Promotion promotion = Promotion.builder()
                .code(code).name(form.getName().trim()).description(trimNullable(form.getDescription()))
                .discountType(form.getDiscountType()).discountValue(form.getDiscountValue())
                .startAt(form.getStartAt()).endAt(form.getEndAt())
                .applicableDays(normalizeDays(form.getApplicableDays()))
                .discountStartTime(form.getDiscountStartTime()).discountEndTime(form.getDiscountEndTime())
                .maxTotalUses(form.getMaxTotalUses()).maxUsesPerCustomer(form.getMaxUsesPerCustomer())
                .owner(owner).createdBy(owner).updatedBy(owner)
                .usedCount(0).reservedCount(0).stopped(false).currentVersion(1)
                .build();
        promotion = promotionRepository.save(promotion);
        saveVersion(promotion, ownerUsername, "Tạo mới khuyến mại");
        auditLogService.logEvent(ownerUsername, SecurityEventType.PROMOTION_CREATED,
                "WEB", "/owner/promotions/new", "Tạo mã " + code);
        return promotion;
    }

    @Transactional
    public Promotion updatePromotion(PromotionForm form, String ownerUsername, HttpSession session) {
        requireReauthentication(session, ownerUsername);
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Chủ sân"));
        Promotion promotion = getLockedOwnerPromotion(owner.getId(), form.getId());
        if (promotion.isStopped()) {
            throw new IllegalStateException("Không thể kích hoạt lại hoặc chỉnh sửa chương trình đã ngừng áp dụng");
        }
        validateForm(form, promotion);
        if (form.getMaxTotalUses() != null
                && form.getMaxTotalUses() < promotion.getUsedCount() + promotion.getReservedCount()) {
            throw new IllegalArgumentException("Tổng lượt sử dụng không được thấp hơn usedCount + reservedCount");
        }

        String oldName = promotion.getName();
        BigDecimal oldValue = promotion.getDiscountValue();
        DiscountType oldType = promotion.getDiscountType();
        LocalDateTime oldStartAt = promotion.getStartAt();
        LocalDateTime oldEndAt = promotion.getEndAt();
        promotion.setName(form.getName().trim());
        promotion.setDescription(trimNullable(form.getDescription()));
        promotion.setDiscountType(form.getDiscountType());
        promotion.setDiscountValue(form.getDiscountValue());
        promotion.setStartAt(form.getStartAt());
        promotion.setEndAt(form.getEndAt());
        promotion.setApplicableDays(normalizeDays(form.getApplicableDays()));
        promotion.setDiscountStartTime(form.getDiscountStartTime());
        promotion.setDiscountEndTime(form.getDiscountEndTime());
        promotion.setMaxTotalUses(form.getMaxTotalUses());
        promotion.setMaxUsesPerCustomer(form.getMaxUsesPerCustomer());
        promotion.setUpdatedBy(owner);
        promotion.setCurrentVersion(promotion.getCurrentVersion() + 1);
        promotion = promotionRepository.save(promotion);
        saveVersion(promotion, ownerUsername, "Chỉnh sửa cấu hình");
        auditLogService.logEvent(ownerUsername, SecurityEventType.PROMOTION_UPDATED,
                "WEB", "/owner/promotions/" + promotion.getId() + "/edit",
                "Mã " + promotion.getCode() + ", phiên bản " + promotion.getCurrentVersion()
                        + "; tên: '" + oldName + "' -> '" + promotion.getName()
                        + "'; mức giảm: " + oldType + " " + oldValue + " -> "
                        + promotion.getDiscountType() + " " + promotion.getDiscountValue()
                        + "; hiệu lực: " + oldStartAt + " - " + oldEndAt + " -> "
                        + promotion.getStartAt() + " - " + promotion.getEndAt());
        return promotion;
    }

    @Transactional
    public void stopPromotion(Long promotionId, String ownerUsername, HttpSession session) {
        requireReauthentication(session, ownerUsername);
        User owner = userRepository.findByUsername(ownerUsername).orElseThrow();
        Promotion promotion = getLockedOwnerPromotion(owner.getId(), promotionId);
        if (promotion.isStopped()) return;
        promotion.setStopped(true);
        promotion.setUpdatedBy(owner);
        promotionRepository.save(promotion);
        auditLogService.logEvent(ownerUsername, SecurityEventType.PROMOTION_STOPPED,
                "WEB", "/owner/promotions/" + promotionId + "/stop",
                "Ngừng mã " + promotion.getCode());
    }

    @Transactional
    public void deletePromotion(Long promotionId, String ownerUsername, HttpSession session) {
        requireReauthentication(session, ownerUsername);
        User owner = userRepository.findByUsername(ownerUsername).orElseThrow();
        Promotion promotion = getLockedOwnerPromotion(owner.getId(), promotionId);
        if (promotion.getUsedCount() > 0 || promotion.getReservedCount() > 0
                || promotionReservationRepository.existsByPromotionId(promotionId)
                || bookingRepository.existsByPromotionCodeIgnoreCase(promotion.getCode())) {
            throw new IllegalStateException("Khuyến mại đã phát sinh lượt sử dụng hoặc giữ chỗ; chỉ được ngừng áp dụng");
        }

        // Chỉ cho xóa khi chưa từng phát sinh lịch sử sử dụng/giữ chỗ. Phiên bản tạo mới vẫn tồn tại.
        promotionRepository.delete(promotion);
        auditLogService.logEvent(ownerUsername, SecurityEventType.PROMOTION_DELETED,
                "WEB", "/owner/promotions/" + promotionId + "/delete",
                "Xóa mã " + promotion.getCode());
    }

    private Promotion getLockedOwnerPromotion(Long ownerId, Long promotionId) {
        Promotion promotion = promotionRepository.findByIdWithLock(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khuyến mại"));
        if (!promotion.getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác khuyến mại thuộc cơ sở này");
        }
        return promotion;
    }

    private void saveVersion(Promotion p, String changedBy, String note) {
        promotionVersionRepository.save(PromotionVersion.builder()
                .promotionId(p.getId()).versionNumber(p.getCurrentVersion())
                .name(p.getName()).description(p.getDescription()).discountType(p.getDiscountType())
                .discountValue(p.getDiscountValue()).startAt(p.getStartAt()).endAt(p.getEndAt())
                .applicableDays(p.getApplicableDays()).discountStartTime(p.getDiscountStartTime())
                .discountEndTime(p.getDiscountEndTime()).maxTotalUses(p.getMaxTotalUses())
                .maxUsesPerCustomer(p.getMaxUsesPerCustomer()).changedBy(changedBy)
                .changedAt(LocalDateTime.now(VN_ZONE)).changeNote(note).build());
    }

    private void validateForm(PromotionForm form, Promotion existing) {
        if (form.getName() == null || form.getName().isBlank())
            throw new IllegalArgumentException("Tên khuyến mại không được để trống");
        if (form.getCode() == null || form.getCode().isBlank())
            throw new IllegalArgumentException("Mã khuyến mại không được để trống");
        if (form.getDiscountType() == null || form.getDiscountValue() == null
                || form.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Giá trị giảm phải lớn hơn 0");
        if (form.getDiscountType() == DiscountType.PERCENTAGE
                && form.getDiscountValue().compareTo(new BigDecimal("100")) >= 0)
            throw new IllegalArgumentException("Phần trăm giảm phải nhỏ hơn 100");
        if (form.getDiscountType() == DiscountType.FIXED_AMOUNT
                && form.getDiscountValue().stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Giảm cố định phải là số nguyên dương VNĐ");
        if (form.getStartAt() == null || form.getEndAt() == null || !form.getStartAt().isBefore(form.getEndAt()))
            throw new IllegalArgumentException("Thời gian bắt đầu phải trước thời gian kết thúc");
        if (form.getApplicableDays() == null || form.getApplicableDays().isEmpty())
            throw new IllegalArgumentException("Chọn ít nhất một ngày trong tuần");
        if (form.getDiscountStartTime() == null || form.getDiscountEndTime() == null
                || !form.getDiscountStartTime().isBefore(form.getDiscountEndTime()))
            throw new IllegalArgumentException("Giờ bắt đầu khuyến mại phải trước giờ kết thúc");
        List<com.example.football.entity.TimeSlot> activeSlots =
                timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
        if (activeSlots.isEmpty()
                || form.getDiscountStartTime().isBefore(activeSlots.get(0).getStartTime())
                || form.getDiscountEndTime().isAfter(activeSlots.get(activeSlots.size() - 1).getEndTime())) {
            throw new IllegalArgumentException("Khung giờ khuyến mại phải nằm trong giờ hoạt động của sân");
        }
        if (form.getMaxTotalUses() != null && form.getMaxTotalUses() <= 0)
            throw new IllegalArgumentException("Tổng số lượt sử dụng phải là số nguyên dương");
        if (form.getMaxUsesPerCustomer() != null && form.getMaxUsesPerCustomer() <= 0)
            throw new IllegalArgumentException("Số lượt dùng mỗi khách phải là số nguyên dương");
        if (existing != null && !normalizeCode(form.getCode()).equals(existing.getCode()))
            throw new IllegalArgumentException("Không được thay đổi mã khuyến mại sau khi tạo");
        if (existing == null && promotionRepository.existsByCode(normalizeCode(form.getCode())))
            throw new IllegalArgumentException("Mã khuyến mại đã tồn tại");
        for (String day : form.getApplicableDays()) {
            try { DayOfWeek.valueOf(day); } catch (Exception ex) {
                throw new IllegalArgumentException("Ngày áp dụng không hợp lệ");
            }
        }
    }

    public String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDays(List<String> days) {
        return days.stream().map(String::toUpperCase).distinct()
                .collect(Collectors.joining(","));
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public PromotionPreviewResult previewPromotion(Long fieldId, LocalDate date, LocalTime startTime,
                                                    LocalTime endTime, String promoCode, User currentUser) {
        if (promoCode == null || promoCode.isBlank())
            return new PromotionPreviewResult(false, "Vui lòng nhập mã khuyến mại", null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        String code = normalizeCode(promoCode);
        Promotion p = promotionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Mã khuyến mại không tồn tại"));
        FootballField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng"));
        if (field.getCreatedBy() == null || !field.getCreatedBy().getId().equals(p.getOwner().getId()))
            return new PromotionPreviewResult(false, "Mã khuyến mại không áp dụng cho sân này", code,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, p.getCurrentVersion());

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        if (p.isStopped()) return unavailable(code, "Chương trình khuyến mại đã ngừng áp dụng", p);
        if (now.isBefore(p.getStartAt()) || now.isAfter(p.getEndAt()))
            return unavailable(code, "Mã khuyến mại không nằm trong thời gian áp dụng", p);
        if (date == null || !p.getApplicableDays().contains(date.getDayOfWeek().name()))
            return unavailable(code, "Mã khuyến mại không áp dụng cho ngày này", p);

        long overlapMinutes = overlapMinutes(startTime, endTime, p.getDiscountStartTime(), p.getDiscountEndTime());
        if (overlapMinutes <= 0)
            return unavailable(code, "Khung giờ đã chọn không giao với khung giờ khuyến mại", p);

        TimeSlot slot = timeSlotRepository.findAllByOrderByStartTimeAsc().stream()
                .filter(s -> s.getStartTime().equals(startTime) && s.getEndTime().equals(endTime))
                .findFirst().orElse(null);
        if (slot == null)
            return unavailable(code, "Khung giờ không hợp lệ", p);

        BigDecimal originalAmount = field.getBasePricePerSlot()
                .add(slot.getPeakHourExtraFee() == null ? BigDecimal.ZERO : slot.getPeakHourExtraFee());
        long slotMinutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
        BigDecimal eligibleAmount = originalAmount.multiply(BigDecimal.valueOf(overlapMinutes))
                .divide(BigDecimal.valueOf(slotMinutes), 0, RoundingMode.HALF_UP);

        if (p.getMaxTotalUses() != null
                && p.getUsedCount() + p.getReservedCount() >= p.getMaxTotalUses())
            return unavailable(code, "Mã khuyến mại đã hết lượt sử dụng", p);
        if (currentUser != null && p.getMaxUsesPerCustomer() != null) {
            long userUses = promotionReservationRepository.countUserUses(p.getId(), currentUser.getId());
            if (userUses >= p.getMaxUsesPerCustomer())
                return unavailable(code, "Mã khuyến mại đã hết lượt sử dụng", p);
        }

        BigDecimal discount;
        if (p.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = eligibleAmount.multiply(p.getDiscountValue())
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        } else {
            discount = p.getDiscountValue().min(eligibleAmount);
        }
        discount = discount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal minimumRemaining = BigDecimal.ONE;
        if (discount.compareTo(originalAmount.subtract(minimumRemaining)) > 0)
            discount = originalAmount.subtract(minimumRemaining);
        if (discount.compareTo(BigDecimal.ZERO) < 0) discount = BigDecimal.ZERO;
        BigDecimal total = originalAmount.subtract(discount).max(BigDecimal.ONE);

        return new PromotionPreviewResult(true, "Mã khuyến mại có thể áp dụng",
                code, originalAmount, discount, total, p.getCurrentVersion());
    }

    private PromotionPreviewResult unavailable(String code, String message, Promotion p) {
        return new PromotionPreviewResult(false, message, code, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, p.getCurrentVersion());
    }

    private long overlapMinutes(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        LocalTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        return start.isBefore(end) ? ChronoUnit.MINUTES.between(start, end) : 0;
    }

    @Transactional
    public PromotionPreviewResult reserveForBooking(Long fieldId, LocalDate date, LocalTime startTime,
                                                     LocalTime endTime, String promoCode, User user,
                                                     Booking booking) {
        Promotion p = promotionRepository.findByCodeWithLock(normalizeCode(promoCode))
                .orElseThrow(() -> new IllegalArgumentException("Mã khuyến mại không tồn tại"));
        PromotionPreviewResult preview = previewPromotion(fieldId, date, startTime, endTime, promoCode, user);
        if (!preview.isApplicable()) throw new IllegalArgumentException(preview.getMessage());

        p.setReservedCount(p.getReservedCount() + 1);
        promotionRepository.save(p);
        promotionReservationRepository.save(PromotionReservation.builder()
                .promotionId(p.getId()).promotionVersionNumber(p.getCurrentVersion())
                .bookingId(booking.getId()).userId(user != null ? user.getId() : null)
                .discountAmount(preview.getDiscountAmount())
                .status(PromotionReservation.ReservationStatus.RESERVED)
                .expiresAt(booking.getHoldExpiresAt()).build());
        return preview;
    }

    @Transactional
    public void confirmReservation(Long bookingId) {
        promotionReservationRepository.findByBookingId(bookingId).ifPresent(found -> {
            Promotion p = promotionRepository.findByIdWithLock(found.getPromotionId()).orElse(null);
            if (p == null) return;
            PromotionReservation r = promotionReservationRepository.findByIdWithLock(found.getId()).orElse(null);
            if (r == null || r.getStatus() != PromotionReservation.ReservationStatus.RESERVED) return;
            r.setStatus(PromotionReservation.ReservationStatus.CONFIRMED);
            promotionReservationRepository.save(r);
            p.setReservedCount(Math.max(0, p.getReservedCount() - 1));
            p.setUsedCount(p.getUsedCount() + 1);
            promotionRepository.save(p);
        });
    }

    @Transactional
    public void releaseReservation(Long bookingId) {
        promotionReservationRepository.findByBookingId(bookingId).ifPresent(found -> {
            Promotion p = promotionRepository.findByIdWithLock(found.getPromotionId()).orElse(null);
            if (p == null) return;
            PromotionReservation r = promotionReservationRepository.findByIdWithLock(found.getId()).orElse(null);
            if (r == null || r.getStatus() != PromotionReservation.ReservationStatus.RESERVED) return;
            r.setStatus(PromotionReservation.ReservationStatus.RELEASED);
            promotionReservationRepository.save(r);
            p.setReservedCount(Math.max(0, p.getReservedCount() - 1));
            promotionRepository.save(p);
        });
    }

    public List<PromotionVersion> getVersions(Long ownerId, Long promotionId) {
        getOwnerPromotion(ownerId, promotionId);
        return promotionVersionRepository.findByPromotionIdOrderByVersionNumberDesc(promotionId);
    }
}
