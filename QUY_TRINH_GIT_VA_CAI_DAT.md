# 🌿 QUY TRÌNH LÀM VIỆC NHÓM VỚI GIT & QUY ĐỊNH BẢO VỆ NHÁNH `MAIN`
**(Tài liệu bắt buộc cho toàn bộ thành viên dự án `football-booking-mvc`)**

---

## PHẦN 1: CÁCH TẢI CODE (`CLONE`) TỪ GITHUB VỀ MÁY

Khi bắt đầu tham gia dự án lần đầu tiên, mở **Terminal / Git Bash** tại thư mục muốn lưu dự án và chạy lệnh:

```bash
# 1. Tải toàn bộ mã nguồn từ kho lưu trữ GitHub về máy
git clone <URL_REPOSITORY_GITHUB_CUA_NHOM>

# 2. Di chuyển vào thư mục dự án
cd football-booking

# 3. Kiểm tra trạng thái nhánh hiện tại (Mặc định sau khi clone sẽ ở nhánh main)
git branch
```

> ⚠️ **Lưu ý ngay sau khi Clone:** Tuyệt đối **KHÔNG** viết code trực tiếp khi đang đứng ở nhánh `main`. Hãy chuyển ngay sang **Phần 2** để tạo nhánh làm việc riêng của bạn!

---

## PHẦN 2: CÁCH TẠO NHÁNH RIÊNG & ĐẨY CODE LÊN NHÁNH CÁ NHÂN

Mỗi thành viên khi làm một chức năng mới hoặc sửa lỗi bắt buộc phải tạo một nhánh riêng xuất phát từ nhánh `main` mới nhất.

### 2.1. Quy tắc đặt tên nhánh
- Khi làm chức năng mới: `feature/<ten-thanh-vien>-<ten-chuc-nang>`
  - Ví dụ: `feature/nam-bo-loc-san-bong`
- Khi sửa lỗi (Bugfix): `fix/<ten-thanh-vien>-<ten-loi>`
  - Ví dụ: `fix/huy-loi-validate-sdt`

### 2.2. Các bước tạo nhánh và đẩy code lên GitHub

**Bước 1: Chuyển về nhánh `main` và kéo code mới nhất từ GitHub về**
```bash
git checkout main
git pull origin main
```

**Bước 2: Tạo nhánh cá nhân mới và chuyển sang nhánh đó**
```bash
git checkout -b feature/nam-bo-loc-san-bong
```
*(Gõ `git branch` để kiểm tra: Dấu `*` màu xanh phải nằm ở nhánh `feature/...` của bạn).*

**Bước 3: Lưu các thay đổi trên nhánh cá nhân (`add` & `commit`)**
Sau khi code xong trên nhánh cá nhân:
```bash
# 1. Kiểm tra danh sách các file đã thay đổi
git status

# 2. Đưa các file cần lưu vào vùng chuẩn bị (Staging)
git add .

# 3. Lưu commit với nội dung rõ ràng
git commit -m "feat: them chuc nang loc san bong theo loai san"
```

**Bước 4: Đẩy (`push`) nhánh cá nhân lên GitHub**
```bash
# Lần đầu tiên đẩy nhánh mới lên GitHub:
git push -u origin feature/nam-bo-loc-san-bong

# Các lần đẩy tiếp theo trên cùng nhánh đó chỉ cần gõ:
git push
```

---

## PHẦN 3: CÁCH ĐỒNG BỘ CODE NHÁNH CÁ NHÂN VỚI NHÁNH `MAIN`

Trong quá trình bạn code trên nhánh riêng, nhánh `main` trên GitHub có thể đã có thêm code mới từ các thành viên khác. **Trước khi tạo Pull Request (PR) xin gộp code**, bạn bắt buộc phải đồng bộ nhánh cá nhân với nhánh `main`:

**Bước 1: Đảm bảo toàn bộ code đang làm trên nhánh cá nhân đã được `commit`**
```bash
git status
# Nếu còn file chưa lưu thì chạy: git add . && git commit -m "..."
```

**Bước 2: Tải thông tin cập nhật mới nhất từ GitHub về**
```bash
git fetch origin
```

**Bước 3: Gộp code mới nhất từ `origin/main` vào nhánh cá nhân hiện tại**
Đang đứng tại nhánh cá nhân (`feature/...`), chạy lệnh:
```bash
git merge origin/main
```

**Bước 4: Xử lý sau khi chạy lệnh `merge`**
- **Trường hợp 1 (Không có xung đột):** Git tự động gộp thành công -> Chạy `git push` để cập nhật nhánh cá nhân trên GitHub.
- **Trường hợp 2 (Báo `CONFLICT` - Xung đột code do 2 người sửa cùng 1 dòng):**
  1. Mở các file báo Conflict (có dấu `<<<<<<< HEAD`, `=======`, `>>>>>>> origin/main`) hoặc dùng công cụ **Git -> Resolve Conflicts...** trên IDE.
  2. Trao đổi với thành viên cùng sửa file đó để thống nhất giữ lại đoạn code đúng, xóa các dòng đánh dấu `<<<<<<<`, `=======`, `>>>>>>>`.
  3. Chạy lệnh hoàn tất đồng bộ và đẩy lên nhánh cá nhân:
     ```bash
     git add .
     git commit -m "merge: dong bo code moi nhat tu nhanh main"
     git push
     ```

---

## PHẦN 4: QUY ĐỊNH VỀ TÍNH TOÀN VẸN CỦA CODE & CÁC LƯU Ý QUAN TRỌNG

Để đảm bảo code của cả nhóm luôn thống nhất và không làm hỏng bài của nhau, mọi thành viên phải tuân thủ:

1. **Đảm bảo code không lỗi trước khi đẩy lên nhánh:**
   - Tuyệt đối không đẩy code đang bị lỗi biên dịch (đỏ file) lên GitHub.
   - Không tự ý xóa hoặc sửa code ở các file/chức năng của thành viên khác nếu chưa trao đổi trước.
2. **Bảo toàn cấu trúc & bảo mật của dự án:**
   - Không xóa các chú thích (Comments/Javadoc) sẵn có.
   - Không đẩy các thư mục sinh tự động hoặc cấu hình IDE cá nhân (`target/`, `.idea/`, `.vscode/`) lên Git. Không tự ý sửa file `.gitignore`.
   - Không tự ý Format (Ctrl + Alt + L) lại toàn bộ những file không thuộc phần việc của mình để tránh gây nhiễu lịch sử thay đổi khi Review Code.
3. **Quy chuẩn viết nội dung Commit:**
   - Không đặt tên commit vô nghĩa như `"abc"`, `"update"`, `"fix1"`.
   - Hãy viết rõ: `feat: ...` (thêm tính năng), `fix: ...` (sửa lỗi), `style: ...` (chỉnh giao diện), `docs: ...` (sửa tài liệu).

---

## PHẦN 5: NGHIÊM CẤM ĐẨY CODE TRỰC TIẾP LÊN `MAIN` & CÁCH PHÒNG TRÁNH

### 5.1. Quy tắc bắt buộc đối với mọi thành viên
> 🚫 **TUYỆT ĐỐI KHÔNG THÀNH VIÊN NÀO ĐƯỢC PHÉP CHẠY LỆNH `git push origin main` HOẶC `git push --force`!**

- Nhánh `main` là nhánh chính thức của toàn nhóm, luôn phải ở trạng thái chạy ổn định 100%.
- **Quy trình duy nhất để đưa code vào `main`:**
  1. Đẩy code lên nhánh cá nhân (`git push origin feature/...`).
  2. Lên GitHub tạo **Pull Request (PR)** từ nhánh cá nhân vào nhánh `main`.
  3. Nhóm trưởng (hoặc thành viên khác) kiểm tra thay đổi (Review Code) -> Nếu đạt yêu cầu và không bị Conflict mới bấm **Merge pull request** trên GitHub.

### 5.2. Cách xử lý khi lỡ tay thao tác nhầm trên nhánh `main` ở máy cá nhân
- **Tình huống 1: Đang đứng ở nhánh `main` và lỡ gõ code sửa file (nhưng CHƯA `git commit`)**
  - Chuyển toàn bộ thay đổi đó sang nhánh mới ngay lập tức bằng lệnh:
    ```bash
    git checkout -b feature/ten-nhanh-cua-ban
    ```
- **Tình huống 2: Đang đứng ở nhánh `main` và lỡ gõ `git commit` (nhưng CHƯA `git push`)**
  - Giữ lại commit đó sang nhánh mới và trả nhánh `main` về trạng thái gốc bằng 3 lệnh:
    ```bash
    git branch feature/ten-nhanh-cua-ban
    git reset --hard origin/main
    git checkout feature/ten-nhanh-cua-ban
    ```

### 5.3. Thiết lập khóa nhánh `main` trên GitHub (Dành cho Nhóm trưởng)
Để khóa bằng kỹ thuật trên GitHub (tự động từ chối nếu có người vô tình gõ `git push origin main`), Nhóm trưởng thực hiện:
1. Vào Repository trên GitHub -> Chọn **Settings** -> **Branches** -> **Add branch protection rule**.
2. Ô **Branch name pattern**: Nhập `main`.
3. Tích chọn:
   - ✅ **Require a pull request before merging** (Bắt buộc phải tạo Pull Request mới được gộp vào `main`).
   - ✅ **Require approvals** (Cần ít nhất 1 người duyệt PR).
   - ✅ **Do not allow bypassing the above settings** (Áp dụng cho cả Admin/Nhóm trưởng).
4. Bấm **Create / Save changes**.
