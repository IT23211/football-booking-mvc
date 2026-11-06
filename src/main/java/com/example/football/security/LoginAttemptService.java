package com.example.football.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_DURATION_MINUTES = 15;

    private final Cache<String, AtomicInteger> loginAttemptCache;
    private final Cache<String, AtomicInteger> otpAttemptCache;

    public LoginAttemptService() {
        this.loginAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();

        this.otpAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    public void loginSucceeded(String key) {
        if (key != null) {
            loginAttemptCache.invalidate(key.toLowerCase());
        }
    }

    public void loginFailed(String key) {
        if (key == null) {
            return;
        }
        String normalizedKey = key.toLowerCase();
        AtomicInteger attempts = loginAttemptCache.get(normalizedKey, k -> new AtomicInteger(0));
        int current = attempts.incrementAndGet();
        if (current >= MAX_ATTEMPTS) {
            log.warn("[SECURITY BRUTE-FORCE ALERT] Khóa tạm thời key='{}' trong {} phút do đăng nhập sai {} lần liên tiếp.",
                    normalizedKey, LOCK_DURATION_MINUTES, current);
        }
    }

    public boolean isBlocked(String key) {
        if (key == null) {
            return false;
        }
        AtomicInteger attempts = loginAttemptCache.getIfPresent(key.toLowerCase());
        return attempts != null && attempts.get() >= MAX_ATTEMPTS;
    }

    public void otpSucceeded(String email) {
        if (email != null) {
            otpAttemptCache.invalidate(email.trim().toLowerCase());
        }
    }

    public void otpFailed(String email) {
        if (email == null) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        AtomicInteger attempts = otpAttemptCache.get(normalized, k -> new AtomicInteger(0));
        int current = attempts.incrementAndGet();
        if (current >= MAX_ATTEMPTS) {
            log.warn("[SECURITY OTP BRUTE-FORCE ALERT] Khóa xác thực OTP cho Email='{}' trong {} phút (sai {} lần).",
                    normalized, LOCK_DURATION_MINUTES, current);
        }
    }

    public boolean isOtpBlocked(String email) {
        if (email == null) {
            return false;
        }
        AtomicInteger attempts = otpAttemptCache.getIfPresent(email.trim().toLowerCase());
        return attempts != null && attempts.get() >= MAX_ATTEMPTS;
    }
}
