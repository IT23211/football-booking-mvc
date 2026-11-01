package com.example.football.security;

import com.example.football.entity.User;
import com.example.football.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản hoặc mật khẩu không chính xác"));

        String clientIp = getClientIp();
        boolean isBlocked = loginAttemptService.isBlocked(normalizedUsername)
                || (clientIp != null && loginAttemptService.isBlocked(clientIp));

        return new CustomUserDetails(user, !isBlocked);
    }

    private String getClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader == null || xfHeader.isBlank()) {
                return request.getRemoteAddr();
            }
            return xfHeader.split(",")[0].trim();
        }
        return null;
    }
}
