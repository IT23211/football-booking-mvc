package com.example.football;

import com.example.football.config.SecurityConfig;
import com.example.football.controller.*;
import com.example.football.entity.*;
import com.example.football.enums.*;
import com.example.football.exception.GlobalExceptionHandler;
import com.example.football.integration.payment.PaymentController;
import com.example.football.integration.payment.VnPayService;
import com.example.football.security.*;
import com.example.football.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        AuthController.class,
        HomeController.class,
        CustomerBookingController.class,
        OwnerDashboardController.class,
        OwnerFieldController.class,
        OwnerBookingController.class,
        PaymentController.class
})
@Import({
        SecurityConfig.class,
        LoginSuccessHandler.class,
        LoginFailureHandler.class,
        CustomAccessDeniedHandler.class,
        LoginAttemptService.class,
        GlobalExceptionHandler.class
})
class ControllerAndSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private FootballFieldService fieldService;

    @MockBean
    private TimeSlotService timeSlotService;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private VnPayService vnPayService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("1. Render trang chủ (/) và trang chi tiết sân (/fields/1) bằng Thymeleaf không lỗi")
    void testHomeAndFieldDetailRendering() throws Exception {
        FootballField sampleField = FootballField.builder()
                .id(1L)
                .fieldCode("SAN-5-A1")
                .name("Sân A1 - Cỏ Nhân Tạo")
                .fieldType(FieldType.SAN_5)
                .basePricePerSlot(new BigDecimal("300000.00"))
                .description("Sân cỏ tiêu chuẩn")
                .imageUrl("https://example.com/field.jpg")
                .status(FieldStatus.ACTIVE)
                .build();

        TimeSlot slot1 = TimeSlot.builder()
                .id(1L)
                .slotName("Ca Chiều (16:00 - 17:30)")
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(17, 30))
                .peakHourExtraFee(new BigDecimal("0.00"))
                .active(true)
                .build();

        TimeSlot slot2 = TimeSlot.builder()
                .id(2L)
                .slotName("Ca Giờ Vàng (17:30 - 19:00)")
                .startTime(LocalTime.of(17, 30))
                .endTime(LocalTime.of(19, 0))
                .peakHourExtraFee(new BigDecimal("100000.00"))
                .active(true)
                .build();

        when(fieldService.searchFieldsForCustomer(any(), any())).thenReturn(List.of(sampleField));
        when(fieldService.getFieldById(1L)).thenReturn(sampleField);
        when(timeSlotService.getActiveTimeSlots()).thenReturn(List.of(slot1, slot2));
        when(bookingService.getBookedTimeSlotIds(eq(1L), any(LocalDate.class))).thenReturn(List.of(1L));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/index"));

        mockMvc.perform(get("/fields/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/field-detail"));
    }

    @Test
    @DisplayName("2. Render các trang xác thực (/auth/login, /auth/register, /auth/verify-otp)")
    void testAuthPagesRendering() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));

        mockMvc.perform(get("/auth/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));

        mockMvc.perform(get("/auth/verify-otp").param("email", "khachhang1@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-otp"));
    }

    @Test
    @DisplayName("3. Kiểm tra bảo mật Chặn leo thang đặc quyền: ROLE_CUSTOMER truy cập /owner/** bị chặn 403")
    @WithMockUser(username = "khachhang1", roles = {"CUSTOMER"})
    void testCustomerCannotAccessOwnerZone() throws Exception {
        mockMvc.perform(get("/owner/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/auth/access-denied"));
    }

    @Test
    @DisplayName("4. Kiểm tra ROLE_OWNER truy cập và render toàn bộ trang quản trị (/owner/**) thành công")
    @WithMockUser(username = "owner", roles = {"OWNER"})
    void testOwnerCanAccessAndRenderOwnerPages() throws Exception {
        when(bookingService.getDailyRevenue(any())).thenReturn(new BigDecimal("1500000"));
        when(bookingService.getMonthlyRevenue(any())).thenReturn(new BigDecimal("25000000"));
        when(bookingService.getTotalForfeitedRevenue()).thenReturn(new BigDecimal("400000"));
        when(bookingService.getBookingsForOwner(any(), any())).thenReturn(List.of());
        when(fieldService.getAllFieldsForOwner()).thenReturn(List.of());
        when(fieldService.getAllActiveFields()).thenReturn(List.of());
        when(timeSlotService.getAllTimeSlots()).thenReturn(List.of());
        when(timeSlotService.getActiveTimeSlots()).thenReturn(List.of());
        when(auditLogService.getRecentSecurityLogs()).thenReturn(List.of());

        mockMvc.perform(get("/owner/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/dashboard"));

        mockMvc.perform(get("/owner/fields"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/field-list"));

        mockMvc.perform(get("/owner/fields/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/field-form"));

        mockMvc.perform(get("/owner/timeslots"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/timeslot-manage"));

        mockMvc.perform(get("/owner/bookings"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/booking-manage"));

        mockMvc.perform(get("/owner/bookings/counter-new"))
                .andExpect(status().isOk())
                .andExpect(view().name("owner/counter-booking-form"));
    }

    @Test
    @DisplayName("5. Kiểm tra bảo mật CSRF: POST không có CSRF token bị từ chối 403, POST /auth/logout có CSRF xóa phiên thành công")
    @WithMockUser(username = "khachhang1", roles = {"CUSTOMER"})
    void testCsrfProtectionAndPostLogout() throws Exception {
        // POST /booking/cancel/1 không có CSRF token -> 403 Forbidden
        mockMvc.perform(post("/booking/cancel/1"))
                .andExpect(status().isForbidden());

        // POST /auth/logout kèm CSRF token -> Redirect về /auth/login?logout=true
        mockMvc.perform(post("/auth/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?logout=true"));
    }
}
