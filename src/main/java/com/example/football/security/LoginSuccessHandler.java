package com.example.football.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        String clientIp = getClientIp(request);

        // Reset bộ đếm Brute-Force sau khi đăng nhập thành công
        loginAttemptService.loginSucceeded(username);
        loginAttemptService.loginSucceeded(clientIp);

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean isOwner = authorities.stream()
                .anyMatch(auth -> "ROLE_OWNER".equals(auth.getAuthority()));

        log.info("[AUTH SUCCESS] User='{}' (IP={}) đăng nhập thành công với quyền={}",
                username, clientIp, authorities);

        if (isOwner) {
            response.sendRedirect(request.getContextPath() + "/owner/dashboard");
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

