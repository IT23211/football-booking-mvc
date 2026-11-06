package com.example.football.form;

import com.example.football.enums.FieldStatus;
import com.example.football.enums.FieldType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * Form thêm/sửa sân bóng (Loại bỏ hoàn toàn khái niệm deposit, chỉ thu 100% theo basePricePerSlot).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldForm {

    private Long id;

    @NotBlank(message = "Mã sân bóng không được để trống (VD: SAN5-A)")
    @Size(max = 20, message = "Mã sân bóng tối đa 20 ký tự")
    private String fieldCode;

    @NotBlank(message = "Tên sân bóng không được để trống")
    @Size(max = 100, message = "Tên sân bóng tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn loại sân bóng")
    @Builder.Default
    private FieldType fieldType = FieldType.SAN_7;

    @NotNull(message = "Giá thuê gốc mỗi ca không được để trống")
    @DecimalMin(value = "50000", message = "Giá thuê sân tối thiểu từ 50.000 VNĐ")
    private BigDecimal basePricePerSlot;

    @Size(max = 2000, message = "Mô tả sân tối đa 2000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn trạng thái hoạt động")
    @Builder.Default
    private FieldStatus status = FieldStatus.ACTIVE;

    private String existingImageUrl;

    private MultipartFile imageFile;
}
