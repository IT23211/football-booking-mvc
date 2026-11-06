package com.example.football.security;

import com.example.football.enums.SecurityEventType;
import com.example.football.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditLogService auditLogService;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "ANONYMOUS";
        Long userId = null;
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            userId = cud.getId();
        }
        String clientIp = getClientIp(request);
        String requestedUri = request.getRequestURI();

        auditLogService.logSecurityEvent(
                userId,
                username,
                SecurityEventType.UNAUTHORIZED_403,
                requestedUri,
                clientIp,
                "Cố tình truy cập trái phép vào " + requestedUri + " (" + request.getMethod() + ")"
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("deniedUrl", requestedUri);
        request.getRequestDispatcher("/auth/access-denied").forward(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
