package com.example.football.controller;

import com.example.football.entity.FootballField;
import com.example.football.entity.TimeSlot;
import com.example.football.enums.FieldStatus;
import com.example.football.enums.FieldType;
import com.example.football.form.FieldForm;
import com.example.football.form.TimeSlotForm;
import com.example.football.service.FootballFieldService;
import com.example.football.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/owner")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerFieldController {

    private final FootballFieldService fieldService;
    private final TimeSlotService timeSlotService;

    // =========================================================================
    // 1. QUẢN LÝ SÂN BÓNG (CRUD & CHUYỂN TRẠNG THÁI BẢO TRÌ - SOFT DELETE)
    // =========================================================================

    @GetMapping("/fields")
    public String listFields(Model model) {
        model.addAttribute("fields", fieldService.getAllFieldsForOwner());
        model.addAttribute("fieldStatuses", FieldStatus.values());
        return "owner/field-list";
    }

    @GetMapping("/fields/new")
    public String showNewFieldForm(Model model) {
        if (!model.containsAttribute("fieldForm")) {
            model.addAttribute("fieldForm", FieldForm.builder()
                    .status(FieldStatus.ACTIVE)
                    .fieldType(FieldType.SAN_5)
                    .build());
        }
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("fieldStatuses", FieldStatus.values());
        model.addAttribute("isEdit", false);
        return "owner/field-form";
    }

    @GetMapping("/fields/edit/{id}")
    public String showEditFieldForm(@PathVariable("id") Long id, Model model) {
        FootballField field = fieldService.getFieldById(id);
        FieldForm form = FieldForm.builder()
                .id(field.getId())
                .fieldCode(field.getFieldCode())
                .name(field.getName())
                .fieldType(field.getFieldType())
                .basePricePerSlot(field.getBasePricePerSlot())
                .description(field.getDescription())
                .status(field.getStatus())
                .existingImageUrl(field.getImageUrl())
                .build();

        model.addAttribute("fieldForm", form);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("fieldStatuses", FieldStatus.values());
        model.addAttribute("maintenanceLogs", fieldService.getMaintenanceLogsByField(id));
        model.addAttribute("isEdit", true);
        return "owner/field-form";
    }

    @PostMapping("/fields/save")
    public String saveField(@Valid @ModelAttribute("fieldForm") FieldForm fieldForm,
                            BindingResult bindingResult,
                            Authentication authentication,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldTypes", FieldType.values());
            model.addAttribute("fieldStatuses", FieldStatus.values());
            model.addAttribute("isEdit", fieldForm.getId() != null);
            return "owner/field-form";
        }

        try {
            fieldService.saveOrUpdateField(fieldForm, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    fieldForm.getId() != null
                            ? "Cập nhật thông tin sân bóng thành công!"
                            : "Thêm sân bóng mới thành công!");
            return "redirect:/owner/fields";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("fieldTypes", FieldType.values());
            model.addAttribute("fieldStatuses", FieldStatus.values());
            model.addAttribute("isEdit", fieldForm.getId() != null);
            return "owner/field-form";
        }
    }

    /**
     * Đổi trạng thái sân bóng sang ACTIVE / MAINTENANCE / INACTIVE (Soft Delete).
     */
    @PostMapping("/fields/status/{id}")
    public String changeFieldStatus(@PathVariable("id") Long id,
                                    @RequestParam("status") FieldStatus status,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            fieldService.changeFieldStatus(id, status, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã chuyển trạng thái sân bóng sang: " + status.getDisplayName());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/fields";
    }

    /**
     * Ghi nhận lịch bảo trì định kỳ cho sân bóng.
     */
    @PostMapping("/fields/maintenance")
    public String createMaintenanceLog(@RequestParam("fieldId") Long fieldId,
                                       @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                       @RequestParam("reason") String reason,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            fieldService.createMaintenanceSchedule(fieldId, startDate, endDate, reason, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ghi nhận lịch bảo trì sân từ " + startDate + " đến " + endDate + "!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/fields/edit/" + fieldId;
    }

    // =========================================================================
    // 2. QUẢN LÝ KHUNG GIỜ ĐÁ & PHỤ PHÍ GIỜ VÀNG
    // =========================================================================

    @GetMapping("/timeslots")
    public String manageTimeSlots(@RequestParam(value = "editId", required = false) Long editId,
                                  Model model) {
        model.addAttribute("timeSlots", timeSlotService.getAllTimeSlots());

        if (!model.containsAttribute("timeSlotForm")) {
            if (editId != null) {
                TimeSlot slot = timeSlotService.getTimeSlotById(editId);
                model.addAttribute("timeSlotForm", TimeSlotForm.builder()
                        .id(slot.getId())
                        .slotName(slot.getSlotName())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .peakHourExtraFee(slot.getPeakHourExtraFee())
                        .active(slot.isActive())
                        .build());
            } else {
                model.addAttribute("timeSlotForm", new TimeSlotForm());
            }
        }
        return "owner/timeslot-manage";
    }

    @PostMapping("/timeslots/save")
    public String saveTimeSlot(@Valid @ModelAttribute("timeSlotForm") TimeSlotForm timeSlotForm,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("timeSlots", timeSlotService.getAllTimeSlots());
            return "owner/timeslot-manage";
        }

        try {
            timeSlotService.saveOrUpdateTimeSlot(timeSlotForm);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu thông tin ca đá & phụ phí giờ vàng thành công!");
            return "redirect:/owner/timeslots";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("timeSlots", timeSlotService.getAllTimeSlots());
            return "owner/timeslot-manage";
        }
    }

    @PostMapping("/timeslots/toggle/{id}")
    public String toggleTimeSlot(@PathVariable("id") Long id,
                                 RedirectAttributes redirectAttributes) {
        timeSlotService.toggleActiveStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái hoạt động của khung giờ!");
        return "redirect:/owner/timeslots";
    }
}
