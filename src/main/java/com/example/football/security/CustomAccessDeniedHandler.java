package com.example.football.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "ANONYMOUS";
        String clientIp = getClientIp(request);
        String requestedUri = request.getRequestURI();

        log.warn("[SECURITY PRIVILEGE ESCALATION ALERT] Người dùng '{}' (IP: {}) cố tình truy cập trái phép vào URL: '{}' (Method: {})",
                username, clientIp, requestedUri, request.getMethod());

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

