package com.example.football.controller;

import com.example.football.entity.User;
import com.example.football.form.PromotionPreviewResult;
import com.example.football.service.PromotionService;
import com.example.football.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionApiController {
    private final PromotionService promotionService;
    private final UserService userService;

    @GetMapping("/preview")
    public PromotionPreviewResult preview(@RequestParam Long fieldId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                                          @RequestParam String code,
                                          Authentication authentication) {
        User user = authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? null
                : userService.getByUsername(authentication.getName());
        return promotionService.previewPromotion(fieldId, date, startTime, endTime, code, user);
    }
}
