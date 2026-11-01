package com.example.football;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FootballBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FootballBookingApplication.class, args);
    }
}

