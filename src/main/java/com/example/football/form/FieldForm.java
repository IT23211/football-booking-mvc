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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldForm {

    private Long id;

    @NotBlank(message = "Tên sân bóng không được để trống")
    @Size(max = 120, message = "Tên sân bóng tối đa 120 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn loại sân bóng")
    private FieldType fieldType;

    @NotNull(message = "Giá thuê mỗi ca không được để trống")
    @DecimalMin(value = "50000", message = "Giá thuê sân tối thiểu từ 50.000 VNĐ")
    private BigDecimal pricePerHour;

    @Size(max = 2000, message = "Mô tả sân tối đa 2000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn trạng thái hoạt động")
    @Builder.Default
    private FieldStatus status = FieldStatus.ACTIVE;

    private String existingImageUrl;

    private MultipartFile imageFile;
}

