# UI/UX SKILL: CLEAN RESPONSIVE UI (SPRING BOOT THYMELEAF & BOOTSTRAP 5)

Khi thiết kế, tạo mới hoặc chỉnh sửa bất kỳ file giao diện HTML/Thymeleaf (`src/main/resources/templates/**`) và CSS (`src/main/resources/static/css/**`) nào trong dự án này, BẮT BUỘC tuân thủ nghiêm ngặt 5 quy chuẩn sau:

## 1. Hệ thống lưới ổn định trên đa nền tảng (Responsive Grid)

- Luôn bọc nội dung trang trong `<div class="container py-4 px-3 px-md-4">` (đối với trang khách hàng) hoặc bố cục Flexbox có Sidebar thu gọn (đối với trang quản trị Chủ sân).
- Sử dụng hệ thống lưới 12 cột của Bootstrap 5 (`row g-3` hoặc `row g-4`) với đầy đủ điểm ngắt (breakpoints):
  - Điện thoại (`col-12`): Xếp chồng 1 cột dọc rõ ràng, không bao giờ để tràn ngang màn hình.
  - Máy tính bảng (`col-md-6` hoặc `col-md-4`): Chia 2-3 cột cân đối.
  - Máy tính để bàn (`col-lg-4` hoặc `col-lg-3`): Hiển thị dạng lưới 3-4 cột.
- Thanh điều hướng (`navbar.html`) bắt buộc có nút thu gọn `navbar-toggler` (`navbar-expand-lg`) để đóng/mở menu mượt mà trên điện thoại.

## 2. Phân tách các thành phần bằng Khung viền rõ ràng (Cards & Frames)

- Mọi khối chức năng độc lập (Khung lọc tìm kiếm, Thông tin sân bóng, Bảng chọn khung giờ, Form nhập liệu, Bảng thống kê) đều phải đặt trong thẻ `<div class="card border rounded-3 shadow-sm bg-white mb-4">`.
- Phân tách rõ 3 phần trong mỗi khung:
  - Tiêu đề khung: `<div class="card-header bg-light py-3 fw-semibold">`
  - Nội dung chính: `<div class="card-body p-3 p-md-4">`
  - Chân khung chứa nút hành động (nếu có): `<div class="card-footer bg-white border-top py-3">`

## 3. Chữ viết, Dòng và Khoảng cách (Typography & Readability)

- Sử dụng font chữ hỗ trợ tiếng Việt rõ nét (`Inter`, `Roboto`, `system-ui`, `-apple-system`, `sans-serif`), độ giãn dòng `line-height: 1.5`.
- Phân cấp cỡ chữ rõ ràng:
  - Tiêu đề trang (`h1`, `h2`): `fs-3 fw-bold text-dark mb-3`
  - Tiêu đề khung/mục (`h5`): `fs-5 fw-semibold text-dark mb-2`
  - Chữ nội dung & bảng biểu: `0.95rem - 1rem` (`#212529`)
  - Chữ chú thích phụ: `small text-muted` (`#6c757d`)
- Chống tràn chữ: Dùng `text-truncate` hoặc `text-break` cho các chuỗi dài (như mã đơn, email, mô tả) để không làm vỡ khung thẻ.

## 4. Chuẩn hóa Bảng biểu, Form nhập liệu & Lưới chọn khung giờ

- **Bảng dữ liệu (`<table>`):** Luôn bọc bên trong `<div class="table-responsive">` kèm class `table table-bordered table-hover align-middle mb-0`. Căn phải (`text-end`) cho cột tiền tệ/số lượng, căn giữa (`text-center`) cho cột trạng thái/nút bấm, và căn trái (`text-start`) cho văn bản.
- **Form nhập liệu (`<form>`):** Mỗi ô `<input>`, `<select>`, `<textarea>` bắt buộc có `<label class="form-label fw-medium">` nằm ngay phía trên. Thông báo lỗi (`th:errors`) đặt ngay dưới ô input với class `text-danger small mt-1` để khi báo lỗi không làm xô lệch các cột bên cạnh.
- **Lưới chọn khung giờ đặt sân (Time Slots):** Mỗi ca đá hiển thị trong một khung có chiều cao đồng nhất (`h-100 p-3 border rounded-3 d-flex flex-column justify-content-between`). Phân biệt rõ trạng thái bằng cả màu viền và nhãn chữ (`badge`):
  - Ca còn trống: Viền xanh (`border-success`), nhãn `bg-success` ("Còn trống").
  - Ca đã kín / Bảo trì: Nền xám (`bg-light text-muted`), thuộc tính `disabled`, nhãn `bg-secondary` hoặc `bg-danger` ("Đã kín" / "Bảo trì").

## 5. Nút bấm & Tương thích thiết bị cảm ứng

- Mọi nút bấm (`btn`) và ô chọn phải có độ cao tối thiểu `40px` (`py-2 px-3`) để dễ thao tác trên màn hình cảm ứng điện thoại.
- Nhóm các nút hành động bằng `<div class="d-grid d-sm-flex gap-2 justify-content-sm-end">` để nút tự động trải rộng 100% trên điện thoại và xếp ngang gọn gàng trên máy tính.
- Hình ảnh sân bóng luôn dùng `img-fluid w-100 rounded` kết hợp `style="height: 200px; object-fit: cover;"` để các thẻ sân bóng luôn bằng nhau tuyệt đối.
