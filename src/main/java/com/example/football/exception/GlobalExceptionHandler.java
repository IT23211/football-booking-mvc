package com.example.football.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Bắt lỗi không đủ quyền truy cập hoặc vi phạm IDOR (thao tác đơn của người khác).
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex,
                                              HttpServletRequest request,
                                              Model model) {
        log.warn("[SECURITY ACCESS DENIED / IDOR] URI='{}', Message='{}'",
                request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage() != null
                ? ex.getMessage()
                : "Bạn không có quyền truy cập hoặc thao tác trên tài nguyên này!");
        model.addAttribute("deniedUrl", request.getRequestURI());
        return "error/403";
    }

    /**
     * Bắt lỗi trùng lịch đặt sân khi 2 khách đặt cùng lúc (Race Condition).
     */
    @ExceptionHandler(SlotAlreadyBookedException.class)
    public String handleSlotAlreadyBooked(SlotAlreadyBookedException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        log.warn("[BOOKING CONFLICT] {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    /**
     * Bắt lỗi upload ảnh vượt quá dung lượng cho phép (5MB).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException ex,
                                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Kích thước file ảnh tải lên vượt quá giới hạn cho phép (Tối đa 5MB)!");
        return "redirect:/owner/fields";
    }

    /**
     * Bắt lỗi 404 Không tìm thấy đường dẫn.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoResourceFoundException ex, Model model) {
        model.addAttribute("errorMessage", "Trang hoặc tài nguyên bạn tìm kiếm không tồn tại.");
        return "error/404";
    }

    /**
     * Chốt chặn cuối cùng: Giấu toàn bộ lỗi SQL / Java StackTrace khỏi giao diện người dùng.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         Model model) {
        log.error("[SYSTEM ERROR] Lỗi không mong muốn tại URI='{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("errorMessage",
                "Hệ thống đang bận hoặc gặp sự cố tạm thời. Vui lòng thử lại sau ít phút!");
        return "error/500";
    }
}

