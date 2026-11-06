package com.example.football.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${mail.otp.expiration-minutes:3}")
    private long otpExpirationMinutes;

    @Value("${mail.otp.cooldown-seconds:60}")
    private long otpCooldownSeconds;

    /**
     * Bộ nhớ đệm lưu mã Email OTP 6 chữ số tự động hủy sau 3 phút (180 giây).
     */
    @Bean(name = "otpCache")
    public Cache<String, String> otpCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(otpExpirationMinutes, TimeUnit.MINUTES)
                .maximumSize(5_000)
                .build();
    }

    /**
     * Bộ nhớ đệm chặn gửi lại Email OTP liên tục trong vòng 60 giây (chống spam hộp thư).
     */
    @Bean(name = "otpCooldownCache")
    public Cache<String, Long> otpCooldownCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(otpCooldownSeconds, TimeUnit.SECONDS)
                .maximumSize(5_000)
                .build();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("fields", "timeSlots");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return cacheManager;
    }
}
