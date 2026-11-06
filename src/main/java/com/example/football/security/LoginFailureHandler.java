package com.example.football.security;

import com.example.football.enums.SecurityEventType;
import com.example.football.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String clientIp = getClientIp(request);

        if (username != null && !username.isBlank()) {
            loginAttemptService.loginFailed(username.trim());
        }
        loginAttemptService.loginFailed(clientIp);

        String errorType = "bad_credentials";
        if (exception instanceof LockedException
                || (username != null && loginAttemptService.isBlocked(username.trim()))
                || loginAttemptService.isBlocked(clientIp)) {
            errorType = "locked";
            auditLogService.logSecurityEvent(
                    null,
                    username,
                    SecurityEventType.BRUTE_FORCE_LOCK,
                    "/auth/login",
                    clientIp,
                    "Khóa tạm thời do đăng nhập sai quá 5 lần hoặc tài khoản bị khóa Strike."
            );
        } else if (exception instanceof DisabledException) {
            errorType = "disabled";
        }

        String targetUrl = request.getContextPath() + "/auth/login?error="
                + URLEncoder.encode(errorType, StandardCharsets.UTF_8);
        response.sendRedirect(targetUrl);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
