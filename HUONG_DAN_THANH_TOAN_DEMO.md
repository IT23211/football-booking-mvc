# HƯỚNG DẪN KIỂM THỬ & DEMO THANH TOÁN ĐẶT SÂN QUA VNPAY SANDBOX

Tài liệu này dành cho các thành viên trong nhóm thực hiện kiểm thử (test) và trình diễn (demo) luồng đặt sân bóng đá trực tuyến & thanh toán **100% giá trị ca đá** qua cổng **VNPay Sandbox 2.1.0**.

---

## 1. THÔNG TIN ĐĂNG NHẬP & THẺ NGÂN HÀNG TEST VNPAY

### 1.1. Tài khoản hệ thống Đặt sân (`http://localhost:8080/auth/login`)
Trên trang Đăng nhập đã tích hợp sẵn **nút điền nhanh 1 chạm**, bạn chỉ cần bấm vào nút tương ứng:

| Vai trò | Username | Mật khẩu | Chức năng chính |
| :--- | :--- | :--- | :--- |
| **Khách hàng 1** | `khachhang1` | `123456aA@` | Đặt sân online, thanh toán VNPay 100%, nhận vé QR, xem lịch sử |
| **Khách hàng 2** | `khachhang2` | `123456aA@` | Dùng để kiểm thử chống trùng lịch / chống giữ chỗ ảo / chống IDOR |
| **Chủ sân (Owner)** | `owner` | `123456aA@` | Quản lý sân, đặt sân tại quầy (thu tiền mặt 100%), thống kê doanh thu |

---

### 1.2. Thông tin Thẻ ATM Nội địa Test trên cổng VNPay Sandbox
Khi hệ thống chuyển hướng sang cổng thanh toán chính thức của VNPay Sandbox (`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`), vui lòng sử dụng bộ thông tin thẻ chuẩn của ngân hàng **NCB** dưới đây (tuyệt đối không gõ dấu tiếng Việt ở tên chủ thẻ):

| Trường thông tin trên VNPay | Giá trị nhập | Ghi chú |
| :--- | :--- | :--- |
| **Phương thức thanh toán** | **Thẻ nội địa và tài khoản ngân hàng** | Chọn dòng thứ 2 trên màn hình VNPay |
| **Ngân hàng** | **NCB** (Ngân hàng Quốc Dân) | Bấm vào logo **NCB** |
| **Số thẻ** | `9704198526191432198` | Copy chính xác 19 chữ số (không khoảng trắng) |
| **Tên chủ thẻ** | `NGUYEN VAN A` | Chữ in hoa không dấu |
| **Ngày phát hành** | `07/15` | Tháng 07 năm 2015 |
| **Mã OTP xác nhận** | `123456` | Nhập ở bước xác thực OTP của VNPay |

---

## 2. QUY TRÌNH DEMO THANH TOÁN THÀNH CÔNG (HAPPY PATH)

### Bước 1: Đăng nhập tài khoản Khách hàng
1. Mở trình duyệt truy cập: `http://localhost:8080/auth/login`
2. Bấm vào nút **Khách 1 (`khachhang1`)** trong khung *Chạm để điền nhanh tài khoản mẫu* $\rightarrow$ Nhấn **Đăng nhập ngay**.

### Bước 2: Chọn sân bóng & Khung giờ đá
1. Tại Trang chủ (`http://localhost:8080/`), chọn một sân đang hoạt động (ví dụ: **Sân 5A - Cỏ Nhân Tạo FIFA Pro**) $\rightarrow$ Nhấn **Xem lịch trống & Đặt sân**.
2. Ở mục **1. Chọn Ngày Xem Lịch Trống**, chọn ngày muốn đá (mặc định là hôm nay).
3. Ở mục **2. Chọn Khung Giờ Đá**, bấm chọn 1 ca đang có nhãn màu xanh **Còn trống** (ví dụ: *Ca 4 - Giờ Vàng 1 (17:30 - 19:00)*).
4. Nhập ghi chú nếu cần (ví dụ: *"Thuê thêm 2 quả bóng và 10 áo bib"*) $\rightarrow$ Bấm nút **Đặt Sân & Thanh Toán 100% Ngay (Giữ chỗ 5 phút)**.

### Bước 3: Thanh toán trên cổng VNPay Sandbox
1. Ngay khi bấm nút, hệ thống sẽ:
   - Khóa tạm thời khung giờ đó trong **5 phút** (`status = PENDING_PAYMENT`).
   - Tạo chữ ký bảo mật `HMAC-SHA512` và chuyển hướng trình duyệt sang `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
2. Trên giao diện VNPay Sandbox:
   - Chọn **Thẻ nội địa và tài khoản ngân hàng** $\rightarrow$ Chọn ngân hàng **NCB**.
   - Nhập Số thẻ `9704198526191432198`, Tên chủ thẻ `NGUYEN VAN A`, Ngày phát hành `07/15` $\rightarrow$ Bấm **Tiếp tục**.
   - Bấm **Đồng ý & Tiếp tục** tại hộp thoại điều khoản.
   - Nhập mã OTP: `123456` $\rightarrow$ Bấm **Thanh toán**.

### Bước 4: Nhận Vé Điện Tử QR & Kiểm tra Doanh thu Chủ sân
1. Sau khi thanh toán thành công (`vnp_ResponseCode = 00`), VNPay tự động chuyển hướng về `http://localhost:8080/payment/vnpay-return`.
2. Hệ thống kiểm tra chữ ký số `HMAC-SHA512`, nếu hợp lệ sẽ:
   - Chuyển trạng thái đơn sang **Đã chốt lịch (`CONFIRMED`)** và trạng thái thanh toán sang **Đã thanh toán 100% (`PAID`)**.
   - Hiển thị **Phiếu Xác Nhận Đặt Sân Điện Tử** kèm **Mã QR Check-in tại sân**.
   - Gửi tự động Email biên lai xác nhận đặt sân về hộp thư của khách hàng.

---

## 3. CÁC KỊCH BẢN NGHIỆP VỤ QUAN TRỌNG CẦN DEMO CHO HỘI ĐỒNG / ĐỒNG NGHIỆP

### Kịch bản A: Khách lỡ đóng tab VNPay khi đang giữ chỗ 5 phút (`PENDING_PAYMENT`)
1. Khách tạo đơn đặt sân nhưng vô tình tắt tab trình duyệt VNPay.
2. Khách vào mục **Lịch sử đặt sân** (`http://localhost:8080/booking/my-bookings`).
3. Tại dòng đơn đang ở trạng thái **Chờ thanh toán (5p)**, bấm nút màu xanh **Thanh toán VNPay** để mở lại cổng thanh toán và hoàn tất giao dịch.

### Kịch bản B: Khách bấm "Hủy thanh toán" ngay trên cổng VNPay (`Mã 24`)
1. Tại màn hình nhập thẻ VNPay, bấm nút **Hủy thanh toán** $\rightarrow$ Xác nhận hủy.
2. VNPay trả về `vnp_ResponseCode = 24`.
3. Hệ thống lập tức chuyển trạng thái đơn sang `EXPIRED_UNPAID` và **mở khóa lại khung giờ trên sân bóng ngay lập tức** để người khác có thể đặt.

### Kịch bản C: Chính sách Hủy đơn KHÔNG HOÀN TIỀN (`Strict Non-Refundable`)
1. Sau khi đơn đã thanh toán 100% (`CONFIRMED` / `PAID`), khách hàng vào **Lịch sử đặt sân** (`/booking/my-bookings`) và bấm nút **Hủy (Không hoàn)**.
2. Hệ thống hiển thị hộp thoại cảnh báo: *Đơn đã thanh toán 100% khi hủy sẽ KHÔNG ĐƯỢC HOÀN TIỀN*.
3. Khi khách xác nhận hủy:
   - Trạng thái đơn chuyển sang **Đã hủy (Không hoàn tiền)** (`CANCELLED_NO_REFUND`), cột khóa `active_slot_lock` về `NULL` $\rightarrow$ **Khung giờ đó lập tức sáng xanh trở lại (Còn trống)** để Chủ sân bán tiếp cho khách khác.
   - Trạng thái tiền chuyển sang **Mất tiền do hủy/vắng (`FORFEITED`)** và **100% số tiền vẫn được giữ nguyên trong doanh thu của Chủ sân**.

### Kịch bản D: Chống giữ chỗ ảo (Quá 5 phút không thanh toán 3 lần $\rightarrow$ Khóa tài khoản 24h)
1. Mỗi tài khoản chỉ được phép có tối đa **1 đơn giữ chỗ (`PENDING_PAYMENT`)** tại một thời điểm.
2. Nếu khách tạo đơn giữ chỗ rồi bỏ ngang không thanh toán sau **5 phút**, tiến trình chạy ngầm (`BookingCleanupScheduler`) sẽ tự động thu hồi sân và cộng `+1 Strike` vào tài khoản.
3. Khi vi phạm đủ **3 lần (`strike_count = 3`)**, tài khoản tự động bị khóa **24 giờ** và ghi nhận vào bảng nhật ký an ninh trên Dashboard Chủ sân.

---

## 4. CHUYỂN ĐỔI NHANH GIỮA VNPAY SANDBOX THẬT & MÀN HÌNH GIẢ LẬP OFFLINE

Trong trường hợp máy demo không có kết nối Internet hoặc cổng `sandbox.vnpayment.vn` của VNPay đang bảo trì, bạn chỉ cần mở file `src/main/resources/application.properties` (dòng 80):

```properties
# Đặt false: Chuyển hướng sang cổng VNPay Sandbox thật (https://sandbox.vnpayment.vn)
# Đặt true : Dùng màn hình giả lập VNPay nội bộ (/payment/mock-vnpay) không cần Internet
vnpay.mock-enabled=false
```
