# 📘 GIỚI THIỆU & HƯỚNG DẪN SỬ DỤNG HỆ THỐNG ĐẶT SÂN BÓNG ĐÁ (`football-booking-mvc`)

---

## PHẦN 1: GIỚI THIỆU TỔNG QUAN VỀ HỆ THỐNG

**Football Booking MVC** là hệ thống Web Full-Stack phục vụ quản lý và đặt lịch sân bóng đá mini/sân liên hợp (Sân 5, Sân 7, Sân 11) trực tuyến theo thời gian thực. Hệ thống được thiết kế theo kiến trúc **Spring Boot 3 MVC + Thymeleaf Server-Side Rendering** kết hợp **Spring Security 6** với cơ chế **Bảo mật đa tầng (Defense-in-Depth)**.

### Các tính năng kỹ thuật & bảo mật nổi bật:
1. **Tách biệt hoàn toàn phân quyền Chủ sân (`ROLE_OWNER`) và Khách hàng (`ROLE_CUSTOMER`):**
   - Khóa kép ở cả tầng URL (`SecurityFilterChain`) và tầng phương thức (`@PreAuthorize("hasRole('OWNER')")`).
   - Tự động ghi log cảnh báo an ninh khi có người dùng cố tình truy cập trái phép vào `/owner/**`.
2. **Chống đặt trùng lịch tuyệt đối (Anti-Race Condition):**
   - Ứng dụng khóa bi quan `@Lock(LockModeType.PESSIMISTIC_WRITE)` của JPA/MySQL (`SELECT ... FOR UPDATE`), đảm bảo nếu 2 khách hàng bấm đặt cùng 1 sân, cùng 1 ca vào cùng 1 mili-giây thì chỉ 1 người thành công, người thứ 2 nhận thông báo *"Khung giờ này vừa có người đặt!"*.
3. **Chống tấn công Mass Assignment, IDOR, CSRF & Brute-Force:**
   - Sử dụng `RegisterForm` tách biệt hoàn toàn với Entity `User`, cưỡng bức gán `ROLE_CUSTOMER` trong `UserService`.
   - Kiểm tra chính chủ nghiêm ngặt trong `BookingService` trước khi xem vé QR hoặc hủy đơn (Chống IDOR).
   - Tự động khóa tạm thời tài khoản/IP trong **15 phút** nếu đăng nhập sai hoặc nhập sai mã OTP quá **5 lần liên tiếp**.
4. **Xác thực SMS OTP Việt Nam & Thanh toán VNPay HMAC-SHA512 + Mã QR ZXing:**
   - Kiểm tra chuẩn đầu số di động Việt Nam (`03x, 05x, 07x, 08x, 09x`), lưu OTP tự hủy sau **3 phút** trên Caffeine Cache và chặn spam gửi lại trong **60 giây**.
   - Băm và kiểm định chữ ký số `HMAC-SHA512` cho cổng thanh toán VNPay, tự động sinh mã QR check-in nhận sân bằng thư viện Google ZXing.

---

## PHẦN 2: HƯỚNG DẪN SỬ DỤNG DÀNH CHO KHÁCH HÀNG (`ROLE_CUSTOMER`)

### Bước 1: Đăng ký tài khoản & Xác thực mã OTP Số điện thoại
1. Trên thanh menu trên cùng, bấm nút **Đăng ký OTP** (`/auth/register`).
2. Nhập **Tên đăng nhập**, **Họ và tên**, **Số điện thoại Việt Nam** (10 số đầu `03, 05, 07, 08, 09`), **Mật khẩu** và bấm **Đăng ký & Nhận mã OTP**.
3. Hệ thống chuyển sang màn hình **Xác thực mã OTP** (`/auth/verify-otp`).
4. Lấy mã OTP 6 chữ số (từ tin nhắn SMS hoặc cửa sổ Console IDE khi chạy Local) nhập vào ô và bấm **Xác nhận & Kích hoạt tài khoản**.

### Bước 2: Đăng nhập hệ thống
1. Truy cập `/auth/login`, nhập tài khoản vừa kích hoạt (hoặc dùng tài khoản mẫu: `khachhang1` / `Khach@123`).
2. Lưu ý: Nếu nhập sai mật khẩu quá 5 lần liên tiếp, tài khoản/IP sẽ bị khóa bảo vệ trong 15 phút.

### Bước 3: Tìm kiếm sân bóng & Kiểm tra khung giờ trống theo ngày
1. Tại **Trang chủ** (`/`), bạn có thể lọc nhanh theo loại sân (**Sân 5 người**, **Sân 7 người**, **Sân 11 người**) hoặc nhập tên sân vào ô tìm kiếm.
2. Bấm nút **Xem giờ trống** ở sân bóng bạn muốn đặt để vào trang chi tiết (`/fields/{id}`).
3. Tại mục **1. Chọn Ngày Xem Lịch Trống**, chọn ngày bạn muốn đá bóng. Hệ thống lập tức tải lưới khung giờ của ngày đó:
   - Các ca có nhãn màu xanh **"Còn trống"**: Bạn có thể tích chọn để đặt sân.
   - Các ca màu xám có nhãn **"Đã kín"**: Đã có người đặt trước và bị vô hiệu hóa (`disabled`), không thể chọn.

### Bước 4: Đặt sân & Thanh toán trực tuyến qua VNPay
1. Tích chọn 1 khung giờ **Còn trống** (hệ thống hiển thị rõ giá tiêu chuẩn cộng phụ phí giờ vàng nếu có).
2. Chọn phương thức thanh toán:
   - **Thanh toán VNPay Sandbox:** Hệ thống tạo đơn ở trạng thái `PENDING_PAYMENT`, ký số `HMAC-SHA512` và chuyển hướng sang cổng thanh toán VNPay. Sau khi bấm xác nhận thanh toán thành công (Mã `00`), hệ thống kiểm tra chữ ký trả về tại `/payment/vnpay-return`, chuyển trạng thái đơn sang **Đã xác nhận (`CONFIRMED`)** và xuất phiếu **Mã QR Check-in nhận sân**.
   - **Thanh toán tại sân / Chuyển khoản sau:** Đơn được tạo ở trạng thái **Chờ chủ sân duyệt (`PENDING_APPROVAL`)**.

### Bước 5: Xem lịch sử đặt sân & Hủy đơn chính chủ
1. Bấm vào menu **Lịch sử đặt sân của tôi** (`/booking/my-bookings`).
2. Tại đây bạn có thể bấm **Vé QR** để xem lại mã QR nhận sân hoặc bấm **Hủy đơn** đối với các đơn chưa hoàn thành.

---

## PHẦN 3: HƯỚNG DẪN SỬ DỤNG DÀNH CHO CHỦ SÂN (`ROLE_OWNER`)

### Bước 1: Đăng nhập & Truy cập Bảng điều khiển (Owner Dashboard)
1. Đăng nhập tại `/auth/login` bằng tài khoản Chủ sân: `owner` / `Owner@123`.
2. Hệ thống tự động nhận diện quyền `ROLE_OWNER` và chuyển hướng thẳng vào **Owner Dashboard** (`/owner/dashboard`).
3. Tại đây Chủ sân theo dõi tức thì:
   - **Doanh thu hôm nay** và **Doanh thu tháng này** (tự động tổng hợp từ các đơn `CONFIRMED` và `COMPLETED`).
   - Số lượng đơn đang chờ duyệt, tổng số sân bóng và danh sách lịch đá trong ngày hôm nay.

### Bước 2: Quản lý Sân bóng (CRUD & Chuyển trạng thái Bảo trì - Soft Delete)
1. Chọn menu bên trái **Quản lý Sân bóng (CRUD)** (`/owner/fields`).
2. **Thêm sân mới:** Bấm nút **+ Thêm Sân Bóng Mới**, nhập tên sân, loại sân (Sân 5 / Sân 7 / Sân 11), đơn giá mỗi ca, mô tả và tải lên ảnh mặt cỏ sân bóng.
3. **Cập nhật sân:** Bấm nút **Sửa** tại dòng sân tương ứng để điều chỉnh giá hoặc thay ảnh mới.
4. **Chuyển trạng thái Bảo trì / Ngưng hoạt động (Soft Delete):**
   - Khi sân cần bảo dưỡng mặt cỏ hoặc ngưng hoạt động, Chủ sân chỉ cần chọn hộp trạng thái ngay trên bảng để đổi sang **Đang bảo trì (`MAINTENANCE`)** hoặc **Ngưng hoạt động (`INACTIVE`)**.
   - Cơ chế này giúp khóa không cho khách đặt mới nhưng vẫn giữ nguyên vẹn toàn bộ hóa đơn lịch sử gắn với sân đó.

### Bước 3: Quản lý Ca đá & Phụ phí Giờ vàng
1. Chọn menu **Ca đá & Phụ phí Giờ vàng** (`/owner/timeslots`).
2. Chủ sân có thể thêm ca đá mới, chỉnh sửa khung giờ bắt đầu - kết thúc và thiết lập **Phụ phí giờ cao điểm** (ví dụ `+120.000 đ` cho các ca giờ vàng `17:30 - 19:00` và `19:00 - 20:30`), hoặc tạm khóa/mở từng ca đá.

### Bước 4: Duyệt đơn, Hủy đơn của khách & Tạo đơn đặt sân trực tiếp tại quầy
1. Chọn menu **Quản lý Đơn & Đặt tại quầy** (`/owner/bookings`).
2. **Lọc đơn:** Lọc nhanh theo ngày nhận sân hoặc theo trạng thái đơn.
3. **Duyệt / Hoàn thành / Hủy đơn:**
   - Bấm **Duyệt**: Chuyển đơn sang trạng thái **Đã xác nhận (`CONFIRMED`)**.
   - Bấm **Xong**: Đánh dấu ca đá đã diễn ra hoàn tất (**Hoàn thành - `COMPLETED`**).
   - Bấm **Hủy**: Chuyển đơn sang **Đã hủy (`CANCELLED`)**, hệ thống lập tức giải phóng khung giờ đó để khách hàng khác có thể đặt lại.
4. **Đặt sân trực tiếp tại quầy (Dành cho khách gọi điện / vãng lai):**
   - Bấm nút **+ Tạo Đơn Đặt Sân Tại Quầy**, chọn sân, ngày đá, khung giờ, nhập tên và số điện thoại khách gọi đặt rồi bấm **Tạo Đơn & Khóa Lịch Sân Ngay**.
   - Hệ thống kiểm tra chống trùng lịch, tự động xác nhận đơn (`CONFIRMED`) và ghi nhận giao dịch thu tiền tại quầy (`COUNTER_CASH`).

