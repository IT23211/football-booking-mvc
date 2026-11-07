package com.example.football.config;

import com.example.football.security.CustomAccessDeniedHandler;
import com.example.football.security.LoginFailureHandler;
import com.example.football.security.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Bật CSRF mặc định cho mọi POST/PUT/DELETE, chỉ bỏ qua cho Webhook IPN tự động
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/payment/ipn", "/payment/ipn/**")
                )

                // 2. Phân quyền chặt chẽ theo URL (Defense-in-Depth)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/images/**", "/uploads/**", "/js/**", "/favicon.ico", "/", "/fields/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/register", "/auth/verify-otp", "/auth/resend-otp", "/auth/forgot-password", "/auth/reset-password").permitAll()
                        .requestMatchers("/auth/access-denied", "/payment/vnpay-return", "/payment/ipn", "/payment/ipn/**").permitAll()
                        .requestMatchers("/owner/**", "/admin/**").hasRole("OWNER")
                        .requestMatchers("/booking/**", "/payment/mock-vnpay", "/payment/mock-vnpay/**").hasAnyRole("CUSTOMER", "OWNER")
                        .anyRequest().authenticated()
                )

                // 3. Cấu hình Form Đăng nhập
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                )

                // 4. Đăng xuất bắt buộc qua phương thức POST kèm CSRF token và xóa sạch JSESSIONID
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .permitAll()
                )

                // 5. Chống Session Fixation & Giới hạn số phiên đồng thời
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1)
                        .expiredUrl("/auth/login?expired=true")
                )

                // 6. Ghi log cảnh báo khi cố tình leo thang đặc quyền sang /owner/**
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // 7. Cấu hình Security Headers (CSP, X-Frame-Options, XSS Protection)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com; " +
                                "img-src 'self' data: https:;"
                        ))
                );

        return http.build();
    }
}
