# HỆ THỐNG QUẢN LÝ VÀ ĐẶT SÂN BÓNG ĐÁ TRỰC TUYẾN (`football-booking-mvc`)

Dự án Web Quản lý và Đặt sân bóng đá được xây dựng theo kiến trúc **Spring Boot 3 MVC thuần kết hợp Thymeleaf (Server-Side Rendering)** cho cả Backend và Frontend trong cùng một project, cơ sở dữ liệu **MySQL 8.0+**, tích hợp **Spring Security 6**, **Xác thực OTP qua Email (`JavaMailSender`)**, **Caffeine Cache**, **Cổng thanh toán VNPay HMAC-SHA512** và **Cơ chế chống phá hoại / chống giữ chỗ ảo 5 phút**.

---

## 1. CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ / Thư viện |
| :--- | :--- |
| **Ngôn ngữ & Nền tảng** | Java 17+, Spring Boot 3.3.5 |
| **Giao diện (Frontend SSR)** | Thymeleaf + `thymeleaf-extras-springsecurity6` + Bootstrap 5.3 + Bootstrap Icons |
| **Cơ sở dữ liệu** | MySQL 8.0+ (`Generated Column` + `PESSIMISTIC_WRITE`), Spring Data JPA / Hibernate |
| **Bảo mật đa tầng** | Spring Security 6 (BCrypt 12 rounds, CSRF Protection, RBAC, Session Fixation, Anti-Brute-Force) |
| **Gửi Email OTP & Vé điện tử** | `spring-boot-starter-mail` (`JavaMailSender` - Gmail SMTP + `@Async`) |
| **Bộ nhớ đệm (In-Memory Cache)** | Caffeine Cache (Lưu mã OTP 6 số tự hủy sau 3 phút, Cooldown 60s chống spam, chống Brute-Force) |
| **Cổng thanh toán & Mã QR** | VNPay Sandbox (`commons-codec` HMAC-SHA512) + Google ZXing (`3.5.3`) tạo mã QR Check-in |

---

## 2. 5 QUY TẮC NGHIỆP VỤ CỐT LÕI & CHỐNG PHÁ HOẠI

1. **Không đặt cọc - Bắt buộc thanh toán 100% hoặc Đặt trực tiếp tại quầy:**
   - Khách hàng (`ROLE_CUSTOMER`) đặt sân qua Web (`ONLINE_CUSTOMER`) bắt buộc thanh toán **100% tổng giá trị ca đá (`total_price = field_price + extra_fee`)** qua VNPay / VietQR.
   - Chỉ có Chủ sân (`ROLE_OWNER`) mới được quyền tạo đơn trực tiếp tại quầy (`COUNTER_OWNER`) và thu 100% tiền mặt (`CASH_AT_COUNTER`).
2. **Không hoàn tiền dưới mọi hình thức (`STRICT NON-REFUNDABLE - FORFEITED`):**
   - Loại bỏ hoàn toàn trạng thái hoàn tiền (`REFUNDED`).
   - Khi đơn đã thanh toán 100% (`CONFIRMED`), nếu khách hủy đơn (`CANCELLED_NO_REFUND`) hoặc vắng mặt không đến (`NO_SHOW`), trạng thái thanh toán chuyển sang `FORFEITED` (Chủ sân giữ nguyên 100% doanh thu bồi thường thiệt hại) và **khung giờ sân lập tức được mở khóa** để bán lại cho khách khác.
3. **Chống tấn công giữ chỗ ảo (`Anti-Slot Hoarding / Inventory DoS`):**
   - Đơn đặt online ở trạng thái `PENDING_PAYMENT` chỉ giữ khóa sân tối đa **5 phút** (`hold_expires_at = created_at + 5 minutes`).
   - Mỗi tài khoản chỉ được giữ tối đa **1 đơn `PENDING_PAYMENT`** tại một thời điểm.
   - Tiến trình ngầm `BookingCleanupScheduler` chạy mỗi 60 giây: tự động chuyển các đơn quá hạn 5 phút sang `EXPIRED_UNPAID` (nhả khóa sân), cộng `+1` vào `strike_count` của User. Nếu `strike_count >= 3`, tự động khóa tài khoản 24 giờ và ghi log `ACCOUNT_LOCKED_STRIKES`.
4. **Chống đặt trùng sân cấp độ Database & Transaction:**
   - Kết hợp cột ảo MySQL 8 `active_slot_lock TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED') THEN 1 ELSE NULL END) STORED` kèm `UNIQUE (field_id, booking_date, time_slot_id, active_slot_lock)` và khóa bi quan `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
5. **Xác thực Email OTP & Bảo mật đa tầng (`/auth` -> `/owner`):**
   - Đăng ký tài khoản yêu cầu Email hợp lệ (`@Email`) và SĐT Việt Nam `^(0[3|5|7|8|9])+([0-9]{8})$`, kích hoạt bằng mã OTP 6 chữ số gửi qua Email (hết hạn sau 3 phút).
   - Khóa chặt `/owner/**` bằng `SecurityConfig` + `@PreAuthorize("hasRole('OWNER')")`, chống Mass Assignment, chống IDOR và chống giả mạo chữ ký thanh toán VNPay (`CHECKSUM_TAMPERED`).

---

## 3. CẤU TRÚC THƯ MỤC DỰ ÁN

```text
football-booking/
├── pom.xml
├── README.md
├── HUONG_DAN_SU_DUNG.md
├── database/
│   └── schema_and_data.sql             # Script tạo 7 bảng MySQL 8.0+ & Seed Data chuẩn
└── src/
    ├── main/
    │   ├── java/com/example/football/
    │   │   ├── FootballBookingApplication.java
    │   │   ├── config/                 # SecurityConfig, WebMvcConfig, CacheConfig, DataInitializer
    │   │   ├── security/               # CustomUserDetails, LoginAttemptService, Handlers
    │   │   ├── enums/                  # 9 Enums: Role, FieldType, FieldStatus, BookingStatus, PaymentStatus...
    │   │   ├── entity/                 # 7 Entities: User, FootballField, TimeSlot, Booking, PaymentTransaction...
    │   │   ├── repository/             # 7 Spring Data JPA Repositories (kèm PESSIMISTIC_WRITE)
    │   │   ├── form/                   # RegisterForm, OtpVerifyForm, CustomerBookingForm, CounterBookingForm...
    │   │   ├── service/                # UserService, FootballFieldService, TimeSlotService, BookingService,
    │   │   │                           # BookingCleanupScheduler, AuditLogService
    │   │   ├── integration/
    │   │   │   ├── mail/               # EmailOtpService (JavaMailSender + Console Mock), VnPhoneUtils
    │   │   │   └── payment/            # VnPayService (HMAC-SHA512 + ZXing QR), PaymentController
    │   │   ├── controller/             # Auth, Home, CustomerBooking, OwnerDashboard, OwnerField, OwnerBooking
    │   │   └── exception/              # GlobalExceptionHandler, SlotAlreadyBookedException
    │   └── resources/
    │       ├── application.properties
    │       ├── static/                 # css/, js/, uploads/fields/
    │       └── templates/              # fragments/, auth/, customer/, owner/, error/
    └── test/                           # Bộ kiểm thử JUnit 5 + MockMvc toàn diện
```

---

## 4. TÀI KHOẢN MẪU CHẠY THỬ (TỰ ĐỘNG KHỞI TẠO)

| Vai trò | Tên đăng nhập (`username`) | Mật khẩu (`password`) | Quyền hạn |
| :--- | :--- | :--- | :--- |
| **Chủ sân (Owner)** | `owner` | `123456aA@` | Toàn quyền quản trị `/owner/**`, CRUD sân, ca đá, đặt tại quầy thu 100% tiền mặt |
| **Khách hàng 1** | `khachhang1` | `123456aA@` | Đặt sân trực tuyến 100% qua VNPay, xem vé QR, lịch sử đặt sân chính chủ |
| **Khách hàng 2** | `khachhang2` | `123456aA@` | Dùng để kiểm thử chống IDOR (thử truy cập/hủy đơn của `khachhang1`) |

> Xem chi tiết hướng dẫn cài đặt MySQL, cấu hình Gmail App Password 16 ký tự và kịch bản kiểm thử tại file [HUONG_DAN_SU_DUNG.md](file:///d:/Web/QLSB/football-booking/HUONG_DAN_SU_DUNG.md).
