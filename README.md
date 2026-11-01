# ⚽ HỆ THỐNG WEB QUẢN LÝ & ĐẶT SÂN BÓNG ĐÁ FULL-STACK (`football-booking-mvc`)
*(Spring Boot 3 MVC + Thymeleaf + MySQL 8 + Spring Security 6 + VNPay HMAC-SHA512 + SMS OTP Việt Nam)*

---

## 1. Công cụ cần cài đặt trước khi chạy dự án (Prerequisites)

Để tải và chạy hệ thống trên máy tính cá nhân (Local), bạn cần cài đặt sẵn các phần mềm sau:
1. **Java Development Kit (JDK):** Phiên bản **JDK 17** hoặc **JDK 21** (Khuyến nghị JDK 21 LTS).
2. **Apache Maven:** Phiên bản 3.8+ (Dự án đã tích hợp sẵn công cụ `mvnw` / `mvnw.cmd` nên có thể chạy ngay cả khi chưa cài Maven toàn cục).
3. **Hệ quản trị cơ sở dữ liệu MySQL:** Phiên bản **MySQL 8.0+**.
4. **Git:** Dùng để clone mã nguồn từ kho lưu trữ.
5. **IDE lập trình:** **IntelliJ IDEA** (Community hoặc Ultimate) hoặc Eclipse / VS Code.

---

## 2. Hướng dẫn tải xuống từ Git và cài đặt chạy trên máy Local

### Bước 1: Clone dự án từ Git và di chuyển vào thư mục gốc
Mở Terminal / PowerShell / Git Bash và chạy lệnh:
```bash
git clone https://github.com/your-username/football-booking-mvc.git
cd football-booking-mvc
```

### Bước 2: Khởi tạo Cơ sở dữ liệu MySQL (`football_booking`)
Mở MySQL Workbench hoặc dòng lệnh MySQL CLI, đăng nhập với quyền `root` và chạy câu lệnh SQL sau để tạo Database chuẩn tiếng Việt (`utf8mb4`):
```sql
CREATE DATABASE IF NOT EXISTS football_booking
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

### Bước 3: Cấu hình kết nối MySQL, SMS OTP Mock và VNPay Sandbox
Mở file cấu hình tại đường dẫn: `src/main/resources/application.properties` và kiểm tra/chỉnh sửa các thông số sau cho khớp với máy của bạn:

```properties
# 1. Chỉnh sửa username và password MySQL trên máy của bạn
spring.datasource.url=jdbc:mysql://localhost:3306/football_booking?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=123456

# 2. Chế độ Mock SMS OTP (Đặt bằng true khi chạy máy Local để in mã OTP 6 số ra Console)
sms.otp.mock-enabled=true
sms.otp.ttl-seconds=180
sms.otp.cooldown-seconds=60

# 3. Cấu hình cổng thanh toán VNPay Sandbox (Đặt vnpay.mock-enabled=true để dùng cổng giả lập nội bộ)
vnpay.mock-enabled=true
vnpay.tmn-code=DEMO_TMN_CODE
vnpay.hash-secret=DEMO_VNPAY_HASH_SECRET_KEY_2026_FOOTBALL_BOOKING
vnpay.return-url=http://localhost:8080/payment/vnpay-return
```

### Bước 4: Khởi chạy ứng dụng

#### Cách 1: Chạy bằng dòng lệnh Maven (Terminal / PowerShell)
- Trên **Windows**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- Trên **macOS / Linux**:
  ```bash
  ./mvnw spring-boot:run
  ```

#### Cách 2: Chạy trực tiếp trên IntelliJ IDEA
1. Mở IntelliJ IDEA -> chọn **File** -> **Open...** -> trỏ tới thư mục dự án.
2. Đợi Maven tải đầy đủ các thư viện trong `pom.xml`.
3. **Lưu ý quan trọng về Lombok:** Vào **File** -> **Settings** -> **Build, Execution, Deployment** -> **Compiler** -> **Annotation Processors** -> Tích chọn **Enable annotation processing** -> Bấm **Apply & OK**.
4. Mở file `src/main/java/com/example/football/FootballBookingApplication.java` và bấm nút **Run (▶)**.
5. Mở trình duyệt truy cập tại địa chỉ: **http://localhost:8080**

---

## 3. Bảng thông tin Tài khoản mặc định (`DataInitializer`)

Ngay khi ứng dụng khởi động lần đầu tiên, class `DataInitializer` sẽ tự động tạo sẵn **2 tài khoản mẫu**, **7 khung giờ đá** và **6 sân bóng mẫu**:

| Vai trò (Role) | Tên đăng nhập (`username`) | Mật khẩu (`password`) | Số điện thoại | Đường dẫn sau khi đăng nhập |
| :--- | :--- | :--- | :--- | :--- |
| **Chủ sân (`ROLE_OWNER`)** | `owner` | `Owner@123` | `0901234567` | `/owner/dashboard` |
| **Khách hàng (`ROLE_CUSTOMER`)** | `khachhang1` | `Khach@123` | `0987654321` | `/` (Trang chủ đặt sân) |

---

## 4. Hướng dẫn cách xem mã OTP 6 số trên cửa sổ Console khi test Đăng ký ở máy Local

Khi biến `sms.otp.mock-enabled=true` được bật trong `application.properties`:
1. Truy cập trang đăng ký: `http://localhost:8080/auth/register`.
2. Điền thông tin tài khoản mới kèm số điện thoại di động Việt Nam hợp lệ (10 số bắt đầu bằng `03, 05, 07, 08, 09`, ví dụ: `0912345678`) và bấm **Đăng ký & Nhận mã OTP**.
3. Nhìn vào cửa sổ **Run / Console của IntelliJ IDEA (hoặc Terminal)**, bạn sẽ thấy khung thông báo nổi bật như sau:
   ```text
   ====================================================================
    [SMS OTP MOCK CONSOLE - CHẾ ĐỘ TEST LOCAL]
    -> Số điện thoại nhận : 0912345678 (84912345678)
    -> MÃ XÁC THỰC OTP    : 482915
    -> Thời hạn hiệu lực  : 3 phút (180 giây)
   ====================================================================
   ```
4. Sao chép 6 chữ số đó (ví dụ `482915`) nhập vào trang `/auth/verify-otp` để kích hoạt tài khoản.

---

## 5. Cách xử lý các lỗi thường gặp (Troubleshooting)

1. **Lỗi trùng cổng `Port 8080 was already in use`:**
   - Nguyên nhân: Có một ứng dụng khác đang chiếm cổng `8080`.
   - Cách khắc phục: Đổi giá trị `server.port=8081` trong `application.properties` (đồng thời cập nhật `vnpay.return-url=http://localhost:8081/payment/vnpay-return`).
2. **Lỗi kết nối MySQL (`Access denied for user 'root'@'localhost'`):**
   - Nguyên nhân: Mật khẩu MySQL trong `application.properties` chưa khớp với mật khẩu MySQL trên máy của bạn.
   - Cách khắc phục: Sửa lại dòng `spring.datasource.password=...` trong `src/main/resources/application.properties`.
3. **Lỗi không nhận diện được hàm `get...` / `set...` / `builder()` của Lombok:**
   - Nguyên nhân: IDE chưa bật Annotation Processing.
   - Cách khắc phục: Bật **Enable annotation processing** trong phần cài đặt Compiler của IntelliJ IDEA và chọn **Build -> Rebuild Project**.

