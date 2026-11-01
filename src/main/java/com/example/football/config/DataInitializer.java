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

    @Override
    @Transactional
    public void run(String... args) {
        initDefaultUsers();
        initDefaultTimeSlots();
        initDefaultFootballFields();
    }

    private void initDefaultUsers() {
        if (!userRepository.existsByUsername("owner")) {
            User owner = User.builder()
                    .username("owner")
                    .password(passwordEncoder.encode("Owner@123"))
                    .fullName("Nguyễn Quản Lý (Chủ Sân)")
                    .phone("0901234567")
                    .phoneVerified(true)
                    .enabled(true)
                    .role(Role.ROLE_OWNER)
                    .build();
            userRepository.save(owner);
            log.info("[DATA INIT] Đã khởi tạo tài khoản Chủ sân: owner / Owner@123");
        }

        if (!userRepository.existsByUsername("khachhang1")) {
            User customer = User.builder()
                    .username("khachhang1")
                    .password(passwordEncoder.encode("Khach@123"))
                    .fullName("Trần Văn Khách")
                    .phone("0987654321")
                    .phoneVerified(true)
                    .enabled(true)
                    .role(Role.ROLE_CUSTOMER)
                    .build();
            userRepository.save(customer);
            log.info("[DATA INIT] Đã khởi tạo tài khoản Khách hàng: khachhang1 / Khach@123");
        }
    }

    private void initDefaultTimeSlots() {
        if (timeSlotRepository.count() == 0) {
            List<TimeSlot> slots = List.of(
                    TimeSlot.builder()
                            .slotName("Ca Sáng 1 (06:00 - 07:30)")
                            .startTime(LocalTime.of(6, 0))
                            .endTime(LocalTime.of(7, 30))
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Sáng 2 (07:30 - 09:00)")
                            .startTime(LocalTime.of(7, 30))
                            .endTime(LocalTime.of(9, 0))
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Chiều 1 (14:30 - 16:00)")
                            .startTime(LocalTime.of(14, 30))
                            .endTime(LocalTime.of(16, 0))
                            .peakHourExtraFee(BigDecimal.ZERO)
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Chiều 2 (16:00 - 17:30)")
                            .startTime(LocalTime.of(16, 0))
                            .endTime(LocalTime.of(17, 30))
                            .peakHourExtraFee(new BigDecimal("50000"))
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Giờ Vàng 1 (17:30 - 19:00)")
                            .startTime(LocalTime.of(17, 30))
                            .endTime(LocalTime.of(19, 0))
                            .peakHourExtraFee(new BigDecimal("120000"))
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Giờ Vàng 2 (19:00 - 20:30)")
                            .startTime(LocalTime.of(19, 0))
                            .endTime(LocalTime.of(20, 30))
                            .peakHourExtraFee(new BigDecimal("120000"))
                            .active(true)
                            .build(),
                    TimeSlot.builder()
                            .slotName("Ca Tối Muộn (20:30 - 22:00)")
                            .startTime(LocalTime.of(20, 30))
                            .endTime(LocalTime.of(22, 0))
                            .peakHourExtraFee(new BigDecimal("60000"))
                            .active(true)
                            .build()
            );
            timeSlotRepository.saveAll(slots);
            log.info("[DATA INIT] Đã khởi tạo {} khung giờ đá mẫu.", slots.size());
        }
    }

    private void initDefaultFootballFields() {
        if (fieldRepository.count() == 0) {
            List<FootballField> fields = List.of(
                    FootballField.builder()
                            .name("Sân A1 - Cỏ Nhân Tạo FIFA Pro (5 người)")
                            .fieldType(FieldType.SAN_5)
                            .pricePerHour(new BigDecimal("250000"))
                            .description("Sân 5 người mặt cỏ nhân tạo đạt chuẩn FIFA Quality, hệ thống đèn LED chống chói 400W, lưới chắn bóng mới 100%, có khu vực ghế nghỉ có mái che.")
                            .imageUrl("https://images.unsplash.com/photo-1529900748604-07564a03e7a6?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .build(),
                    FootballField.builder()
                            .name("Sân A2 - Cỏ Nhân Tạo Cao Cấp (5 người)")
                            .fieldType(FieldType.SAN_5)
                            .pricePerHour(new BigDecimal("250000"))
                            .description("Sân 5 người thoáng mát, thoát nước siêu tốc khi trời mưa, hạt cao su êm chân chống chấn thương cổ chân, miễn phí trà đá và wifi.")
                            .imageUrl("https://images.unsplash.com/photo-1575361204480-aadea25e6e68?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .build(),
                    FootballField.builder()
                            .name("Sân B1 - Sân 7 Tiêu Chuẩn Giải Phong Trào")
                            .fieldType(FieldType.SAN_7)
                            .pricePerHour(new BigDecimal("450000"))
                            .description("Sân 7 người kích thước chuẩn 40m x 60m, chuyên tổ chức các giải bóng đá phủi và giao hữu công ty. Có khán đài mini và phòng thay đồ riêng.")
                            .imageUrl("https://images.unsplash.com/photo-1518605368461-1ee7e53f2056?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .build(),
                    FootballField.builder()
                            .name("Sân B2 - Sân 7 Khán Đài VIP")
                            .fieldType(FieldType.SAN_7)
                            .pricePerHour(new BigDecimal("480000"))
                            .description("Sân 7 người chất lượng cao, cỏ mới thay định kỳ 6 tháng, hỗ trợ thuê trọng tài, áo bib và bóng thi đấu Động Lực chính hãng.")
                            .imageUrl("https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .build(),
                    FootballField.builder()
                            .name("Sân C1 - Sân Lớn 11 Người Liên Hợp")
                            .fieldType(FieldType.SAN_11)
                            .pricePerHour(new BigDecimal("950000"))
                            .description("Sân 11 người tiêu chuẩn thi đấu chuyên nghiệp, mặt cỏ dày êm ái, dàn đèn cao áp công suất lớn phục vụ đá giải ban đêm.")
                            .imageUrl("https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.ACTIVE)
                            .build(),
                    FootballField.builder()
                            .name("Sân A3 - Sân 5 Khu Vực Đông (Đang bảo trì)")
                            .fieldType(FieldType.SAN_5)
                            .pricePerHour(new BigDecimal("240000"))
                            .description("Sân đang trong quá trình bảo dưỡng thay thảm cỏ định kỳ để nâng cấp chất lượng phục vụ khách hàng.")
                            .imageUrl("https://images.unsplash.com/photo-1551958219-acbc608c6377?auto=format&fit=crop&w=900&q=80")
                            .status(FieldStatus.MAINTENANCE)
                            .build()
            );
            fieldRepository.saveAll(fields);
            log.info("[DATA INIT] Đã khởi tạo {} sân bóng mẫu.", fields.size());
        }
    }
}

