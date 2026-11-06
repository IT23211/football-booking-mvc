package com.example.football.config;

import com.example.football.entity.FootballField;
import com.example.football.entity.TimeSlot;
import com.example.football.entity.User;
import com.example.football.enums.FieldStatus;
import com.example.football.enums.FieldType;
import com.example.football.enums.Role;
import com.example.football.repository.FootballFieldRepository;
import com.example.football.repository.TimeSlotRepository;
import com.example.football.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final FootballFieldRepository fieldRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        ensureGeneratedColumnAndUniqueConstraint();
        User owner = initDefaultUsers();
        initDefaultTimeSlots();
        initDefaultFootballFields(owner);
    }

    /**
     * Đảm bảo cột ảo active_slot_lock và ràng buộc UNIQUE chống đặt trùng cấp độ Database
     * luôn được thiết lập chuẩn xác trên MySQL 8.0+ ngay cả khi Hibernate tự tạo bảng (ddl-auto=update).
     * Lưu ý MySQL 8: Không cho phép MODIFY trực tiếp cột thường sang GENERATED COLUMN (Error 3106),
     * cần kiểm tra INFORMATION_SCHEMA.COLUMNS, nếu chưa phải GENERATED thì DROP và ADD lại cột.
     */
    private void ensureGeneratedColumnAndUniqueConstraint() {
        try {
            String extra = jdbcTemplate.queryForObject("""
                    SELECT EXTRA FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'bookings'
                      AND COLUMN_NAME = 'active_slot_lock'
                    """, String.class);

            if (extra == null || !extra.toUpperCase().contains("GENERATED")) {
                try {
                    jdbcTemplate.execute("ALTER TABLE bookings DROP INDEX uk_prevent_double_booking");
                } catch (Exception ignored) {
                }
                try {
                    jdbcTemplate.execute("ALTER TABLE bookings DROP COLUMN active_slot_lock");
                } catch (Exception ignored) {
                }
                jdbcTemplate.execute("""
                        ALTER TABLE bookings
                        ADD COLUMN active_slot_lock TINYINT GENERATED ALWAYS AS (
                            CASE WHEN status IN ('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED') THEN 1 ELSE NULL END
                        ) STORED
                        """);
                log.info("[DB SCHEMA INIT] Đã chuyển cột active_slot_lock sang GENERATED ALWAYS AS ... STORED.");
            }
        } catch (Exception ex) {
            log.debug("[DB SCHEMA INIT] Kiểm tra cột active_slot_lock: {}", ex.getMessage());
        }

        try {
            Integer idxCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'bookings'
                      AND INDEX_NAME = 'uk_prevent_double_booking'
                    """, Integer.class);
            if (idxCount != null && idxCount == 0) {
                jdbcTemplate.execute("""
                        ALTER TABLE bookings
                        ADD CONSTRAINT uk_prevent_double_booking UNIQUE (field_id, booking_date, time_slot_id, active_slot_lock)
                        """);
                log.info("[DB SCHEMA INIT] Đã gắn ràng buộc UNIQUE uk_prevent_double_booking.");
            }
        } catch (Exception ex) {
            log.debug("[DB SCHEMA INIT] Kiểm tra uk_prevent_double_booking: {}", ex.getMessage());
        }
    }

    private User initDefaultUsers() {
        User owner = userRepository.findByUsername("owner").orElseGet(() -> User.builder()
                .username("owner")
                .fullName("Nguyễn Quản Lý (Chủ Sân)")
                .email("owner@footballvn.com")
                .phone("0901234567")
                .role(Role.ROLE_OWNER)
                .strikeCount(0)
                .failedAttempt(0)
                .build());
        owner.setPassword(passwordEncoder.encode("123456aA@"));
        if (owner.getEmail() == null || owner.getEmail().isBlank()) {
            owner.setEmail("owner@footballvn.com");
        }
        owner.setRole(Role.ROLE_OWNER);
        owner.setEmailVerified(true);
        owner.setEnabled(true);
        owner.setLockUntil(null);
        owner = userRepository.save(owner);
        log.info("[DATA INIT] Tài khoản Chủ sân sẵn sàng: owner / 123456aA@");

        User customer1 = userRepository.findByUsername("khachhang1").orElseGet(() -> User.builder()
                .username("khachhang1")
                .fullName("Trần Văn Khách")
                .email("khachhang1@gmail.com")
                .phone("0987654321")
                .role(Role.ROLE_CUSTOMER)
                .strikeCount(0)
                .failedAttempt(0)
                .build());
        customer1.setPassword(passwordEncoder.encode("123456aA@"));
        if (customer1.getEmail() == null || customer1.getEmail().isBlank()) {
            customer1.setEmail("khachhang1@gmail.com");
        }
        customer1.setRole(Role.ROLE_CUSTOMER);
        customer1.setEmailVerified(true);
        customer1.setEnabled(true);
        customer1.setLockUntil(null);
        userRepository.save(customer1);
        log.info("[DATA INIT] Tài khoản Khách hàng 1 sẵn sàng: khachhang1 / 123456aA@");

        User customer2 = userRepository.findByUsername("khachhang2").orElseGet(() -> User.builder()
                .username("khachhang2")
                .fullName("Lê Hoàng Nam")
                .email("khachhang2@gmail.com")
                .phone("0912345678")
                .role(Role.ROLE_CUSTOMER)
                .strikeCount(0)
                .failedAttempt(0)
                .build());
        customer2.setPassword(passwordEncoder.encode("123456aA@"));
        if (customer2.getEmail() == null || customer2.getEmail().isBlank()) {
            customer2.setEmail("khachhang2@gmail.com");
        }
        customer2.setRole(Role.ROLE_CUSTOMER);
        customer2.setEmailVerified(true);
        customer2.setEnabled(true);
        customer2.setLockUntil(null);
        userRepository.save(customer2);

        return owner;
    }

    private void initDefaultTimeSlots() {
        if (timeSlotRepository.count() == 0) {
            List<TimeSlot> slots = List.of(
                    TimeSlot.builder()
                            .slotName("Ca 1 - Sáng Sớm (06:00 - 07:30)")
                            .startTime(LocalTime.of(6, 0))
                            .endTime(LocalTime.of(7, 30))
                            .peakHour(false)
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca 2 - Sáng (07:30 - 09:00)")
                            .startTime(LocalTime.of(7, 30))
                            .endTime(LocalTime.of(9, 0))
                            .peakHour(false)
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca 3 - Chiều (16:00 - 17:30)")
                            .startTime(LocalTime.of(16, 0))
                            .endTime(LocalTime.of(17, 30))
                            .peakHour(false)
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca 4 - Giờ Vàng 1 (17:30 - 19:00)")
                            .startTime(LocalTime.of(17, 30))
                            .endTime(LocalTime.of(19, 0))
                            .peakHour(true)
                            .peakHourExtraFee(new BigDecimal("120000.00"))
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca 5 - Giờ Vàng 2 (19:00 - 20:30)")
                            .startTime(LocalTime.of(19, 0))
                            .endTime(LocalTime.of(20, 30))
                            .peakHour(true)
                            .peakHourExtraFee(new BigDecimal("120000.00"))
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca 6 - Tối Muộn (20:30 - 22:00)")
                            .startTime(LocalTime.of(20, 30))
                            .endTime(LocalTime.of(22, 0))
                            .peakHour(true)
                            .peakHourExtraFee(new BigDecimal("60000.00"))
                            .active(true)
                            .build()
            );
            timeSlotRepository.saveAll(slots);
            log.info("[DATA INIT] Đã khởi tạo {} khung giờ đá mẫu.", slots.size());
        }
    }

    private void initDefaultFootballFields(User owner) {
        if (fieldRepository.count() == 0) {
            List<FootballField> fields = List.of(
                    FootballField.builder()
                            .fieldCode("SAN5-A")
                            .name("Sân 5A - Cỏ Nhân Tạo FIFA Pro")
                            .fieldType(FieldType.SAN_5)
                            .basePricePerSlot(new BigDecimal("250000.00"))
                            .description("Sân 5 người mặt cỏ nhân tạo đạt chuẩn FIFA Quality, hệ thống đèn LED chống chói 400W, lưới chắn bóng mới 100%.")
                            .imageUrl("https://images.unsplash.com/photo-1529900748604-07564a03e7a6?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .createdBy(owner)
                            .build(),
                    FootballField.builder()
                            .fieldCode("SAN5-B")
                            .name("Sân 5B - Cỏ Nhân Tạo Cao Cấp")
                            .fieldType(FieldType.SAN_5)
                            .basePricePerSlot(new BigDecimal("250000.00"))
                            .description("Sân 5 người thoáng mát, thoát nước siêu tốc khi trời mưa, hạt cao su êm chân chống chấn thương.")
                            .imageUrl("https://images.unsplash.com/photo-1575361204480-aadea25e6e68?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .createdBy(owner)
                            .build(),
                    FootballField.builder()
                            .fieldCode("SAN7-A")
                            .name("Sân 7A - Tiêu Chuẩn Giải Phong Trào")
                            .fieldType(FieldType.SAN_7)
                            .basePricePerSlot(new BigDecimal("450000.00"))
                            .description("Sân 7 người kích thước chuẩn 40m x 60m, có khán đài mini có mái che và phòng thay đồ riêng.")
                            .imageUrl("https://images.unsplash.com/photo-1518605368461-1ee7e53f2056?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .createdBy(owner)
                            .build(),
                    FootballField.builder()
                            .fieldCode("SAN11-A")
                            .name("Sân 11A - Sân Lớn Liên Hợp Quốc Tế")
                            .fieldType(FieldType.SAN_11)
                            .basePricePerSlot(new BigDecimal("950000.00"))
                            .description("Sân 11 người tiêu chuẩn thi đấu chuyên nghiệp, dàn đèn cao áp công suất lớn phục vụ đá giải ban đêm.")
                            .imageUrl("https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .createdBy(owner)
                            .build()
            );
            fieldRepository.saveAll(fields);
            log.info("[DATA INIT] Đã khởi tạo {} sân bóng mẫu.", fields.size());
        }
    }
}
