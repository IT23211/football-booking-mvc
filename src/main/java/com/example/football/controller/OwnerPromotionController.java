
package com.example.football.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner")
public class OwnerPromotionController {

    @GetMapping("/promotions")
    public String showPromotions() {
        return "owner/promotions";
    }
}
