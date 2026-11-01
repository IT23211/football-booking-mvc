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

    @Value("${sms.otp.ttl-seconds:180}")
    private long otpTtlSeconds;

    @Value("${sms.otp.cooldown-seconds:60}")
    private long otpCooldownSeconds;

    /**
     * Bộ nhớ đệm lưu mã OTP 6 chữ số tự động hết hạn sau 3 phút (180 giây).
     */
    @Bean(name = "otpCache")
    public Cache<String, String> otpCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(otpTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(5_000)
                .build();
    }

    /**
     * Bộ nhớ đệm chặn gửi lại SMS OTP liên tục trong vòng 60 giây (chống spam SMS).
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

