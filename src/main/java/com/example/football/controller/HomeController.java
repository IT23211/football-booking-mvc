package com.example.football.controller;

import com.example.football.entity.FootballField;
import com.example.football.entity.TimeSlot;
import com.example.football.enums.FieldType;
import com.example.football.form.CustomerBookingForm;
import com.example.football.service.BookingService;
import com.example.football.service.FootballFieldService;
import com.example.football.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final FootballFieldService fieldService;
    private final TimeSlotService timeSlotService;
    private final BookingService bookingService;

    /**
     * Trang chủ & Danh sách sân bóng có bộ lọc theo loại sân (Sân 5, Sân 7, Sân 11) và từ khóa.
     */
    @GetMapping({"/", "/fields"})
    public String index(@RequestParam(value = "type", required = false) FieldType selectedType,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        Model model) {
        List<FootballField> fields = fieldService.searchFieldsForCustomer(selectedType, keyword);
        model.addAttribute("fields", fields);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("selectedType", selectedType);
        model.addAttribute("keyword", keyword);
        return "customer/index";
    }

    /**
     * Trang chi tiết sân bóng & kiểm tra khung giờ trống theo ngày.
     * Nếu không truyền tham số date thì mặc định là LocalDate.now().
     */
    @GetMapping("/fields/{id}")
    public String fieldDetail(@PathVariable("id") Long fieldId,
                              @RequestParam(value = "date", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              Model model) {
        LocalDate selectedDate = (date != null && !date.isBefore(LocalDate.now()))
                ? date
                : LocalDate.now();

        FootballField field = fieldService.getFieldById(fieldId);
        List<TimeSlot> timeSlots = timeSlotService.getActiveTimeSlots();
        List<Long> bookedSlotIds = bookingService.getBookedTimeSlotIds(fieldId, selectedDate);

        CustomerBookingForm bookingForm = CustomerBookingForm.builder()
                .fieldId(fieldId)
                .bookingDate(selectedDate)
                .build();

        model.addAttribute("field", field);
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("bookedSlotIds", bookedSlotIds);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("minDate", LocalDate.now());
        model.addAttribute("bookingForm", bookingForm);

        return "customer/field-detail";
    }
}
