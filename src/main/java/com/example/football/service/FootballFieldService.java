package com.example.football.service;

import com.example.football.entity.FieldMaintenanceLog;
import com.example.football.entity.FootballField;
import com.example.football.entity.TimeSlot;
import com.example.football.entity.User;
import com.example.football.enums.FieldStatus;
import com.example.football.enums.FieldType;
import com.example.football.form.FieldForm;
import com.example.football.repository.FieldMaintenanceLogRepository;
import com.example.football.repository.FootballFieldRepository;
import com.example.football.repository.TimeSlotRepository;
import com.example.football.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FootballFieldService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final FootballFieldRepository fieldRepository;
    private final FieldMaintenanceLogRepository maintenanceLogRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    @Value("${app.upload.field-images-dir:src/main/resources/static/images/fields}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public List<FootballField> searchFieldsForCustomer(FieldType fieldType, String keyword) {
        return fieldRepository.searchAvailableFields(fieldType, keyword);
    }

    @Transactional(readOnly = true)
    public List<FootballField> getAllActiveFields() {
        return fieldRepository.findByStatusOrderByIdAsc(FieldStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<FootballField> getAllFieldsForOwner() {
        return fieldRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public FootballField getFieldById(Long id) {
        return fieldRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân bóng với mã ID: " + id));
    }

    @Transactional
    public FootballField saveOrUpdateField(FieldForm form, String ownerUsername) {
        String normalizedCode = form.getFieldCode().trim().toUpperCase();
        FootballField field;

        if (form.getId() != null) {
            field = getFieldById(form.getId());
            if (!field.getFieldCode().equalsIgnoreCase(normalizedCode)
                    && fieldRepository.existsByFieldCode(normalizedCode)) {
                throw new IllegalArgumentException("Mã sân bóng '" + normalizedCode + "' đã tồn tại!");
            }
        } else {
            if (fieldRepository.existsByFieldCode(normalizedCode)) {
                throw new IllegalArgumentException("Mã sân bóng '" + normalizedCode + "' đã tồn tại!");
            }
            field = new FootballField();
            if (ownerUsername != null) {
                userRepository.findByUsername(ownerUsername).ifPresent(field::setCreatedBy);
            }
        }

        field.setFieldCode(normalizedCode);
        field.setName(form.getName().trim());
        field.setFieldType(form.getFieldType());
        field.setBasePricePerSlot(form.getBasePricePerSlot());
        field.setDescription(form.getDescription() != null ? form.getDescription().trim() : "");
        field.setStatus(form.getStatus() != null ? form.getStatus() : FieldStatus.ACTIVE);

        MultipartFile imageFile = form.getImageFile();
        if (imageFile != null && !imageFile.isEmpty()) {
            String savedImagePath = storeFieldImage(imageFile);
            field.setImageUrl(savedImagePath);
        } else if (field.getImageUrl() == null || field.getImageUrl().isBlank()) {
            field.setImageUrl("https://images.unsplash.com/photo-1529900748604-07564a03e7a6?auto=format&fit=crop&w=900&q=80");
        }

        FootballField saved = fieldRepository.save(field);
        log.info("[OWNER FIELD CRUD] Đã lưu sân bóng id={}, code='{}', name='{}', status={}",
                saved.getId(), saved.getFieldCode(), saved.getName(), saved.getStatus());
        return saved;
    }

    /**
     * Đổi trạng thái sân bóng sang ACTIVE / MAINTENANCE / INACTIVE (Soft Delete).
     */
    @Transactional
    public void changeFieldStatus(Long id, FieldStatus newStatus) {
        FootballField field = getFieldById(id);
        field.setStatus(newStatus);
        fieldRepository.save(field);
        log.info("[OWNER FIELD STATUS] Chuyển trạng thái sân id={} ('{}') sang {}",
                field.getId(), field.getName(), newStatus);
    }

    @Transactional
    public void changeFieldStatus(Long id, FieldStatus newStatus, String ownerUsername) {
        changeFieldStatus(id, newStatus);
    }

    @Transactional
    public void createMaintenanceSchedule(Long fieldId,
                                          LocalDate startDate,
                                          LocalDate endDate,
                                          String reason,
                                          String ownerUsername) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Khoảng ngày bảo trì không hợp lệ!");
        }
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            createMaintenanceLog(fieldId, current, null, reason, ownerUsername);
            current = current.plusDays(1);
        }
        changeFieldStatus(fieldId, FieldStatus.MAINTENANCE);
    }

    @Transactional(readOnly = true)
    public List<FieldMaintenanceLog> getMaintenanceLogsByField(Long fieldId) {
        return maintenanceLogRepository.findByFieldIdOrderByMaintenanceDateDesc(fieldId);
    }

    @Transactional
    public FieldMaintenanceLog createMaintenanceLog(Long fieldId,
                                                    LocalDate maintenanceDate,
                                                    Long timeSlotId,
                                                    String reason,
                                                    String ownerUsername) {
        FootballField field = getFieldById(fieldId);
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản Chủ sân!"));
        TimeSlot slot = (timeSlotId != null)
                ? timeSlotRepository.findById(timeSlotId).orElse(null)
                : null;

        FieldMaintenanceLog logEntry = FieldMaintenanceLog.builder()
                .field(field)
                .maintenanceDate(maintenanceDate)
                .timeSlot(slot)
                .reason(reason != null && !reason.isBlank() ? reason.trim() : "Bảo trì định kỳ")
                .createdBy(owner)
                .build();
        return maintenanceLogRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public List<FieldMaintenanceLog> getMaintenanceLogsByFieldAndDate(Long fieldId, LocalDate date) {
        return maintenanceLogRepository.findByFieldIdAndDate(fieldId, date);
    }

    private String storeFieldImage(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "field.jpg");
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh định dạng JPG, JPEG, PNG hoặc WEBP!");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File tải lên không phải là định dạng hình ảnh hợp lệ!");
        }

        try {
            Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String newFileName = "field-" + UUID.randomUUID() + "." + ext.toLowerCase();
            Path targetFile = targetDir.resolve(newFileName).normalize();
            if (!targetFile.startsWith(targetDir)) {
                throw new SecurityException("Phát hiện đường dẫn file không an toàn (Path Traversal)!");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/images/fields/" + newFileName;
        } catch (IOException ex) {
            log.error("Lỗi khi lưu ảnh sân bóng: {}", ex.getMessage());
            throw new IllegalStateException("Không thể lưu file ảnh tải lên!");
        }
    }
}
