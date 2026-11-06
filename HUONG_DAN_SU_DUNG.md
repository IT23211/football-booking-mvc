# HƯỚNG DẪN CÀI ĐẶT, CẤU HÌNH & KIỂM THỬ HỆ THỐNG ĐẶT SÂN BÓNG ĐÁ

Tài liệu này hướng dẫn chi tiết từng bước để cài đặt cơ sở dữ liệu MySQL 8.0+, cấu hình gửi mã OTP qua Email (`JavaMailSender`), khởi chạy ứng dụng trên IntelliJ IDEA / Eclipse / VS Code và kiểm thử toàn bộ các quy tắc nghiệp vụ bảo mật.

---

## PHẦN 1: YÊU CẦU MÔI TRƯỜNG CÀI ĐẶT

1. **JDK:** Java 17 hoặc Java 21 trở lên.
2. **MySQL Server:** Phiên bản **MySQL 8.0+** (Bắt buộc từ 8.0 trở lên để hỗ trợ cột ảo `GENERATED ALWAYS AS ... STORED`).
3. **Công cụ lập trình (IDE):** IntelliJ IDEA, Eclipse, Spring Tool Suite hoặc VS Code.
4. **Maven:** Dự án đã tích hợp sẵn công cụ `mvnw` (`mvnw.cmd` cho Windows), không bắt buộc cài Maven ngoài.

---

## PHẦN 2: KHỞI TẠO DATABASE MYSQL 8.0+

Bạn có **2 cách** để khởi tạo cơ sở dữ liệu:

### Cách 1: Tự động hoàn toàn khi chạy Spring Boot (Khuyên dùng)
- Mở file `src/main/resources/application.properties`, kiểm tra thông tin tài khoản MySQL của máy bạn:
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/football_booking_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
  spring.datasource.username=root
  spring.datasource.password=123456
  ```
- Khi khởi chạy ứng dụng, Hibernate và class `DataInitializer.java` sẽ tự động tạo database `football_booking_db`, tạo 7 bảng, gắn cột ảo `active_slot_lock` và nạp sẵn 3 tài khoản mẫu + 4 sân bóng + 6 khung giờ đá.

### Cách 2: Chạy file SQL thủ công trong MySQL Workbench / DBeaver
- Mở MySQL Workbench và chạy toàn bộ file script:
  `database/schema_and_data.sql`

---

## PHẦN 3: HƯỚNG DẪN CẤU HÌNH GỬI MÃ OTP QUA EMAIL (GMAIL SMTP & CHẾ ĐỘ MOCK LOCAL)

Hệ thống hỗ trợ 2 chế độ gửi mã OTP 6 chữ số trong file `src/main/resources/application.properties`:

### 1. Chế độ Chạy thử nghiệm trên máy Local (Mặc định: `mail.otp.mock-enabled=true`)
- Không cần cấu hình tài khoản Gmail thật.
- Khi đăng ký tài khoản tại `/auth/register`, hệ thống sẽ in trực tiếp khung chứa **Mã OTP 6 chữ số** ra cửa sổ **Console / Terminal** của IDE:
  ```text
  ====================================================================
  [EMAIL OTP CONSOLE MOCK] GUI MA XAC THUC TAI KHOAN DAT SAN BONG
  -> To Email   : nguyenvana@gmail.com
  -> Ma OTP 6 so: 839201 (Hieu luc 3 phut trong Caffeine Cache)
  ====================================================================
  ```

### 2. Chế độ Gửi Email thực tế tới hộp thư khách hàng (`mail.otp.mock-enabled=false`)
Để hệ thống gửi Email HTML thực tế qua Gmail SMTP:
1. Đăng nhập vào tài khoản Google (Gmail) của bạn -> Vào **Quản lý Tài khoản Google** -> Tab **Bảo mật (Security)**.
2. Bật tính năng **Xác minh 2 bước (2-Step Verification)**.
3. Tại ô tìm kiếm của trang Bảo mật, gõ **"Mật khẩu ứng dụng" (App Passwords)**.
4. Tạo một mật khẩu ứng dụng mới (đặt tên: `FootballBookingApp`), Google sẽ cấp cho bạn **1 chuỗi mật khẩu gồm 16 ký tự** (ví dụ: `abcd efgh ijkl mnop`).
5. Mở file `src/main/resources/application.properties` và cập nhật:
   ```properties
   spring.mail.username=email_cua_ban@gmail.com
   spring.mail.password=abcdefghijklmnop
   mail.otp.mock-enabled=false
   ```

---

## PHẦN 4: CÁCH KHỞI CHẠY DỰ ÁN & ĐĂNG NHẬP TÀI KHOẢN MẪU

### 1. Lệnh chạy ứng dụng
Mở Terminal tại thư mục gốc `football-booking` và chạy:
```powershell
.\mvnw.cmd spring-boot:run
```
Sau khi ứng dụng khởi động thành công, mở trình duyệt truy cập:
- **Trang chủ Khách hàng:** `http://localhost:8080/`
- **Trang Đăng nhập:** `http://localhost:8080/auth/login`
- **Trang Quản trị Chủ sân:** `http://localhost:8080/owner/dashboard`

### 2. Danh sách tài khoản mẫu
- **Tài khoản Chủ sân (`ROLE_OWNER`):**
  - Username: `owner`
  - Password: `123456aA@`
- **Tài khoản Khách hàng 1 (`ROLE_CUSTOMER`):**
  - Username: `khachhang1`
  - Password: `123456aA@`
- **Tài khoản Khách hàng 2 (`ROLE_CUSTOMER`):**
  - Username: `khachhang2`
  - Password: `123456aA@`

---

## PHẦN 5: KỊCH BẢN KIỂM THỬ 5 QUY TẮC NGHIỆP VỤ & BẢO MẬT

### Kịch bản 1: Kiểm thử Đăng ký tài khoản & Xác thực Email OTP (3 phút)
1. Truy cập `http://localhost:8080/auth/register`.
2. Nhập thông tin tài khoản mới, địa chỉ Email hợp lệ và SĐT Việt Nam 10 số.
3. Xem mã OTP 6 chữ số trên Console IDE (hoặc trong hộp thư Email), nhập vào trang `/auth/verify-otp` để kích hoạt tài khoản.

### Kịch bản 2: Kiểm thử Đặt sân Online - Thanh toán 100% qua VNPay Sandbox & Giữ chỗ 5 phút
1. Đăng nhập tài khoản `khachhang1` / `123456aA@`.
2. Chọn sân bóng tại trang chủ -> Chọn ngày và khung giờ trống -> Bấm **"Đặt sân & Thanh toán 100% ngay"**.
3. Đơn được tạo ở trạng thái `PENDING_PAYMENT` (giữ khóa sân đúng 5 phút) và chuyển hướng sang cổng **VNPay Sandbox Mock (`/payment/mock-vnpay`)**.
4. Bấm **"Thanh toán thành công (ResponseCode = 00)"**:
   - Hệ thống kiểm tra chữ ký số `HMAC-SHA512`.
   - Đơn chuyển sang `CONFIRMED`, trạng thái thanh toán `PAID_100` (`paid_amount = total_price`).
   - Hiển thị Vé điện tử kèm **Mã QR Check-in**.

### Kịch bản 3: Kiểm thử Chính sách Không hoàn tiền (`CANCELLED_NO_REFUND` / `NO_SHOW` -> `FORFEITED`)
1. Tại trang `/booking/my-bookings`, khách bấm **"Hủy (Không hoàn tiền)"** trên đơn đã thanh toán (`CONFIRMED`).
2. Hoặc Chủ sân (`owner`) vào `/owner/bookings` bấm **"Vắng mặt (NO_SHOW)"**.
3. Kiểm tra kết quả:
   - Trạng thái thanh toán của đơn chuyển sang `FORFEITED` (Tiền vẫn được tính vào Doanh thu thực thu và thẻ **Tiền bồi thường (FORFEITED)** trên Dashboard của Chủ sân).
   - Quay lại trang chi tiết sân bóng: Khung giờ vừa hủy/vắng mặt đã **tự động mở khóa (`Còn trống`)** để khách khác đặt lại.

### Kịch bản 4: Kiểm thử Chống giữ chỗ ảo (`Anti-Slot Hoarding` & Khóa tài khoản 3 Strikes)
1. Khách hàng chọn khung giờ và bấm đặt sân nhưng **không thanh toán** ở màn hình VNPay.
2. Khi đang có 1 đơn `PENDING_PAYMENT`, nếu khách cố tình quay lại đặt thêm ca khác sẽ bị chặn ngay lập tức.
3. Sau 5 phút không thanh toán, tiến trình `BookingCleanupScheduler` tự động chuyển đơn sang `EXPIRED_UNPAID`, nhả khóa sân và cộng `+1` vào `strike_count`. Đủ 3 lần bỏ ngang, tài khoản tự động bị khóa 24 giờ.

### Kịch bản 5: Kiểm thử Chống truy cập trái phép (`/owner/**` & Chống IDOR)
1. Đang đăng nhập bằng `khachhang1`, gõ trực tiếp `http://localhost:8080/owner/dashboard` -> Hệ thống chặn bằng trang `403 Access Denied` và ghi log vào bảng **Nhật ký Bảo mật (`audit_security_logs`)** trên Dashboard Chủ sân.
2. Đăng nhập bằng `khachhang2`, thử truy cập vé `/booking/result/{id}` của `khachhang1` -> Hệ thống chặn lỗi `403 Access Denied` và ghi log `IDOR_ATTEMPT`.
